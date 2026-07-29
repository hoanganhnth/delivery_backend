package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.client.OrderDecisionEligibilityClient;
import com.delivery.restaurant_service.entity.RestaurantOrderDecision;
import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import com.delivery.restaurant_service.exception.RestaurantDecisionConflictException;
import com.delivery.restaurant_service.repository.RestaurantOrderDecisionRepository;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HexFormat;

/**
 * ✅ Publish sự kiện nhà hàng xác nhận / từ chối đơn tới order-service.
 * Field payload khớp {@code RestaurantEvent} bên order-service (StringJsonMessageConverter
 * map theo tên field).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantOrderEventPublisher {

    private final RestaurantOrderDecisionRepository decisionRepository;
    private final RestaurantOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OrderDecisionEligibilityClient orderEligibilityClient;
    private final RestaurantDecisionLock decisionLock;

    public static final String CONFIRMED_TOPIC = "restaurant.order-confirmed";
    public static final String REJECTED_TOPIC = "restaurant.order-rejected";

    @Value("${app.kafka.topics.order-confirmed:restaurant.order-confirmed}")
    private String confirmedTopic = CONFIRMED_TOPIC;

    @Value("${app.kafka.topics.order-rejected:restaurant.order-rejected}")
    private String rejectedTopic = REJECTED_TOPIC;

    @Transactional
    public void publishConfirmed(Long orderId, Long restaurantId, Long actorUserId,
                                 Integer estimatedPrepTime, String notes) {
        requirePositiveId(orderId, "orderId");
        requirePositiveId(restaurantId, "restaurantId");
        requirePositiveId(actorUserId, "actorUserId");
        requirePositivePrepTime(estimatedPrepTime);
        Map<String, Object> payload = base(orderId, restaurantId, actorUserId, "CONFIRMED", "CONFIRM");
        payload.put("estimatedPrepTime", estimatedPrepTime);
        payload.put("notes", notes);
        persistDecisionAndEvent(orderId, restaurantId, RestaurantOrderDecision.Decision.CONFIRMED,
                confirmedTopic, payload,
                decisionFingerprint(orderId, restaurantId, actorUserId,
                        "CONFIRMED", estimatedPrepTime, notes, null));
    }

    @Transactional
    public void publishRejected(Long orderId, Long restaurantId, Long actorUserId,
                                String rejectionReason) {
        requirePositiveId(orderId, "orderId");
        requirePositiveId(restaurantId, "restaurantId");
        requirePositiveId(actorUserId, "actorUserId");
        requireNonBlank(rejectionReason, "rejectionReason");
        Map<String, Object> payload = base(orderId, restaurantId, actorUserId, "REJECTED", "REJECT");
        payload.put("rejectionReason", rejectionReason);
        persistDecisionAndEvent(orderId, restaurantId, RestaurantOrderDecision.Decision.REJECTED,
                rejectedTopic, payload,
                decisionFingerprint(orderId, restaurantId, actorUserId,
                        "REJECTED", null, null, rejectionReason));
    }

    private void persistDecisionAndEvent(Long orderId, Long restaurantId,
                                         RestaurantOrderDecision.Decision decision,
                                         String topic, Map<String, Object> payload,
                                         String decisionFingerprint) {
        if (orderId == null || restaurantId == null || actorUserIdFrom(payload) == null
                || actorUserIdFrom(payload) <= 0) {
            throw new IllegalArgumentException("orderId, restaurantId and positive actorUserId are required");
        }

        decisionLock.lock(orderId);
        var existing = decisionRepository.findByOrderIdForUpdate(orderId);
        if (existing.isPresent()) {
            RestaurantOrderDecision stored = existing.get();
            if (stored.getRestaurantId().equals(restaurantId) && stored.getDecision() == decision) {
                requireMatchingStoredDecision(stored, decisionFingerprint);
                log.info("Restaurant decision already stored: orderId={}, decision={}", orderId, decision);
                return;
            }
            throw new RestaurantDecisionConflictException(
                    "Order " + orderId + " already has decision " + stored.getDecision());
        }

        orderEligibilityClient.requirePendingOrderForRestaurant(orderId, restaurantId);

        LocalDateTime now = LocalDateTime.now();
        RestaurantOrderDecision stored = new RestaurantOrderDecision();
        stored.setOrderId(orderId);
        stored.setRestaurantId(restaurantId);
        stored.setDecision(decision);
        stored.setPayloadFingerprint(decisionFingerprint);
        stored.setCreatedAt(now);
        decisionRepository.save(stored);

        UUID eventId = UUID.randomUUID();
        ObjectNode eventPayload = objectMapper.valueToTree(payload);
        eventPayload.put("eventId", eventId.toString());
        eventPayload.put("eventType", decision.name());
        eventPayload.put("occurredAt", now.toString());
        eventPayload.put("decisionFingerprint", decisionFingerprint);

        RestaurantOutboxEvent event = new RestaurantOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateId(orderId.toString());
        event.setEventType(decision.name());
        event.setTopic(topic);
        event.setEventKey(orderId.toString());
        event.setPayload(eventPayload.toString());
        event.setStatus(RestaurantOutboxEvent.Status.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        outboxRepository.save(event);
        log.info("Stored restaurant decision {} and outbox event {} for order {}",
                decision, eventId, orderId);
    }

    private Map<String, Object> base(Long orderId, Long restaurantId, Long actorUserId,
                                     String status, String action) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("restaurantId", restaurantId);
        payload.put("actorUserId", actorUserId);
        payload.put("status", status);
        payload.put("action", action);
        payload.put("processedAt", LocalDateTime.now().toString());
        return payload;
    }

    private void requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private void requirePositivePrepTime(Integer estimatedPrepTime) {
        if (estimatedPrepTime == null || estimatedPrepTime <= 0 || estimatedPrepTime > 240) {
            throw new IllegalArgumentException("estimatedPrepTime must be between 1 and 240");
        }
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private Long actorUserIdFrom(Map<String, Object> payload) {
        Object value = payload.get("actorUserId");
        return value instanceof Number number ? number.longValue() : null;
    }

    private void requireMatchingStoredDecision(RestaurantOrderDecision stored, String expectedFingerprint) {
        if (stored.getPayloadFingerprint() != null) {
            if (!stored.getPayloadFingerprint().equals(expectedFingerprint)) {
                throw new RestaurantDecisionConflictException(
                        "Restaurant decision replay has a contradictory payload");
            }
            return;
        }

        // Legacy rows predate the decision-row fingerprint. Fall back to the
        // retained outbox payload when it exists; if the outbox row is absent,
        // preserve the old idempotency behavior instead of breaking old data.
        outboxRepository.findTopByAggregateIdAndEventTypeOrderByCreatedAtDescIdDesc(
                        stored.getOrderId().toString(), stored.getDecision().name())
                .ifPresent(previous -> requireMatchingReplay(previous, expectedFingerprint));
    }

    private void requireMatchingReplay(RestaurantOutboxEvent previous, String expectedFingerprint) {
        try {
            var previousPayload = objectMapper.readTree(previous.getPayload());
            String storedFingerprint = previousPayload.hasNonNull("decisionFingerprint")
                    ? previousPayload.get("decisionFingerprint").asText() : null;
            // V1 rows predate the fingerprint. Preserve their exact legacy
            // idempotency behavior; all new decisions are fail-closed.
            if (storedFingerprint != null && !storedFingerprint.equals(expectedFingerprint)) {
                throw new RestaurantDecisionConflictException(
                        "Restaurant decision replay has a contradictory payload");
            }
        } catch (RestaurantDecisionConflictException conflict) {
            throw conflict;
        } catch (Exception malformed) {
            throw new IllegalStateException("Stored restaurant decision payload is invalid", malformed);
        }
    }

    private String decisionFingerprint(Long orderId, Long restaurantId, Long actorUserId, String decision,
                                       Integer estimatedPrepTime, String notes, String rejectionReason) {
        ObjectNode canonical = objectMapper.createObjectNode();
        canonical.put("orderId", orderId);
        canonical.put("restaurantId", restaurantId);
        canonical.put("actorUserId", actorUserId);
        canonical.put("decision", decision);
        if (estimatedPrepTime == null) canonical.putNull("estimatedPrepTime");
        else canonical.put("estimatedPrepTime", estimatedPrepTime);
        if (notes == null) canonical.putNull("notes");
        else canonical.put("notes", notes);
        if (rejectionReason == null) canonical.putNull("rejectionReason");
        else canonical.put("rejectionReason", rejectionReason);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
