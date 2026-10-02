package com.delivery.restaurant.infrastructure.decision;

import com.delivery.restaurant.application.api.RestaurantDecisionCommand;
import com.delivery.restaurant.application.api.RestaurantDecisionStorePort;
import com.delivery.restaurant.domain.decision.RestaurantDecisionKind;
import com.delivery.restaurant_service.entity.RestaurantOrderDecision;
import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import com.delivery.restaurant_service.repository.RestaurantOrderDecisionRepository;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Persisted decision mapping, canonical fingerprint and wire-compatible outbox envelope. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaRestaurantDecisionAdapter implements RestaurantDecisionStorePort {
    private final RestaurantOrderDecisionRepository decisionRepository;
    private final RestaurantOutboxEventRepository outboxRepository;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;
    private final RestaurantDecisionLock decisionLock;
    @Value("${app.kafka.topics.order-confirmed:restaurant.order-confirmed}")
    private String confirmedTopic;
    @Value("${app.kafka.topics.order-rejected:restaurant.order-rejected}")
    private String rejectedTopic;

    @Override public void lockOrder(Long id) { decisionLock.lock(id); }
    @Override public Optional<StoredDecision> find(Long id) {
        return decisionRepository.findByOrderIdForUpdate(id).map(row -> new StoredDecision(row.getRestaurantId(),
                RestaurantDecisionKind.valueOf(row.getDecision().name()), row.getPayloadFingerprint()));
    }
    @Override public Optional<String> legacyFingerprint(Long id, RestaurantDecisionKind decision) {
        return outboxRepository.findTopByAggregateIdAndEventTypeOrderByCreatedAtDescIdDesc(id.toString(), decision.name())
                .flatMap(row -> {
                    try {
                        var payload = objectMapper.readTree(row.getPayload());
                        return payload.hasNonNull("decisionFingerprint")
                                ? Optional.of(payload.get("decisionFingerprint").asText()) : Optional.empty();
                    } catch (Exception malformed) {
                        throw new IllegalStateException("Stored restaurant decision payload is invalid", malformed);
                    }
                });
    }
    @Override public void insertDecisionAndEvent(RestaurantDecisionCommand command, String fingerprint) {
        LocalDateTime now = LocalDateTime.now();
        var stored = new RestaurantOrderDecision();
        stored.setOrderId(command.orderId()); stored.setRestaurantId(command.restaurantId());
        stored.setDecision(RestaurantOrderDecision.Decision.valueOf(command.decision().name()));
        stored.setPayloadFingerprint(fingerprint); stored.setCreatedAt(now);
        decisionRepository.save(stored);

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", command.orderId()); payload.put("restaurantId", command.restaurantId());
        payload.put("actorUserId", command.actorUserId()); payload.put("status", command.decision().name());
        payload.put("action", command.decision() == RestaurantDecisionKind.CONFIRMED ? "CONFIRM" : "REJECT");
        payload.put("processedAt", now.toString());
        if (command.decision() == RestaurantDecisionKind.CONFIRMED) {
            payload.put("estimatedPrepTime", command.estimatedPrepTime()); payload.put("notes", command.notes());
        } else payload.put("rejectionReason", command.rejectionReason());
        UUID eventId = UUID.randomUUID();
        ObjectNode eventPayload = objectMapper.valueToTree(payload);
        eventPayload.put("eventId", eventId.toString()); eventPayload.put("eventType", command.decision().name());
        eventPayload.put("occurredAt", now.toString()); eventPayload.put("decisionFingerprint", fingerprint);
        var event = new RestaurantOutboxEvent();
        event.setEventId(eventId); event.setAggregateId(command.orderId().toString());
        event.setEventType(command.decision().name());
        event.setTopic(command.decision() == RestaurantDecisionKind.CONFIRMED ? confirmedTopic : rejectedTopic);
        event.setEventKey(command.orderId().toString()); event.setPayload(eventPayload.toString());
        event.setTraceparent(currentTraceparent()); event.setStatus(RestaurantOutboxEvent.Status.PENDING);
        event.setAttempts(0); event.setNextAttemptAt(now); event.setCreatedAt(now); outboxRepository.save(event);
        log.info("Stored restaurant decision {} and outbox event {} for order {}", command.decision(), eventId, command.orderId());
    }

    private String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null || span.context() == null) return null;
        return "00-" + span.context().traceId() + "-" + span.context().spanId()
                + "-" + (Boolean.TRUE.equals(span.context().sampled()) ? "01" : "00");
    }

    @Override public String fingerprint(RestaurantDecisionCommand command) {
        Long orderId = command.orderId(), restaurantId = command.restaurantId(), actorUserId = command.actorUserId();
        String decision = command.decision().name();
        Integer estimatedPrepTime = command.estimatedPrepTime();
        String notes = command.notes(), rejectionReason = command.rejectionReason();
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
