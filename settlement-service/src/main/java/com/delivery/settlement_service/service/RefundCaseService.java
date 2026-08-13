package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.event.OrderCancelledEvent;
import com.delivery.settlement_service.dto.response.RefundCaseResponse;
import com.delivery.settlement_service.dto.response.RefundCustomerCaseResponse;
import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.entity.RefundCase.RefundComponent;
import com.delivery.settlement_service.entity.RefundCase.RefundStatus;
import com.delivery.settlement_service.entity.RefundCase.RefundTrigger;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.repository.RefundCaseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class RefundCaseService {
    private static final int ADMIN_LIST_LIMIT = 100;
    private static final String COD = "COD";
    private static final String ONLINE = "ONLINE";
    private static final String CANCELLED = "CANCELLED";
    private static final String SHIPPER_NOT_FOUND = "SHIPPER_NOT_FOUND";
    private static final String REFUND_ELIGIBLE = "REFUND_ELIGIBLE";

    private final RefundCaseRepository repository;
    private final RefundOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final boolean providerProcessingEnabled;

    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    public RefundCaseService(RefundCaseRepository repository,
                             RefundOutboxService outboxService,
                             ObjectMapper objectMapper,
                             @Value("${app.refund.provider-processing-enabled:false}") boolean providerProcessingEnabled) {
        this.repository = repository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
        this.providerProcessingEnabled = providerProcessingEnabled;
    }

    @Transactional
    public RefundCase processOrderCancellation(OrderCancelledEvent event) {
        validate(event);
        String fingerprint = fingerprint(event);
        RefundTrigger trigger = resolveTrigger(event);
        String idempotencyKey = event.getOrderId() + ":" + trigger.name() + ":ORDER_TOTAL";

        RefundCase existing = findExisting(event, trigger, idempotencyKey);
        if (existing != null) {
            requireExactReplay(existing, event, fingerprint, idempotencyKey);
            return existing;
        }

        RefundStatus status = decideStatus(event, trigger);
        BigDecimal capturedAmount = COD.equals(event.getPaymentMethod())
                ? BigDecimal.ZERO : event.getTotalPrice();
        BigDecimal refundAmount = COD.equals(event.getPaymentMethod())
                ? BigDecimal.ZERO : event.getTotalPrice();
        if (status == RefundStatus.NO_REFUND_REQUIRED) {
            refundAmount = BigDecimal.ZERO;
        }

        RefundCase refundCase = RefundCase.builder()
                .refundId(UUID.randomUUID())
                .eventId(event.getEventId())
                .idempotencyKey(idempotencyKey)
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .restaurantId(event.getRestaurantId())
                .previousOrderStatus(event.getPreviousStatus())
                .currentOrderStatus(event.getCurrentStatus())
                .paymentMethod(event.getPaymentMethod())
                .trigger(trigger)
                .component(RefundComponent.ORDER_TOTAL)
                .status(status)
                .currency("VND")
                .subtotalAmount(event.getSubtotalPrice())
                .discountAmount(event.getDiscountAmount())
                .shippingFee(event.getShippingFee())
                .totalAmount(event.getTotalPrice())
                .capturedAmount(capturedAmount)
                .refundAmount(refundAmount)
                .actorSource(actorSource(event))
                .actorId(event.getCancelledBy())
                .reason(event.getCancelReason())
                .payloadFingerprint(fingerprint)
                .attempts(0)
                .build();

        // A retry from another Kafka partition can arrive before this listener
        // commits. Let PostgreSQL resolve all refund identity constraints, then
        // distinguish exact replay from a conflicting event before anything is
        // sent to the provider outbox.
        if (insertIfAbsent(refundCase) == 0) {
            RefundCase concurrent = findExisting(event, trigger, idempotencyKey);
            if (concurrent == null) {
                throw new IllegalStateException(
                        "refund case conflict resolved without a committed refund case");
            }
            requireExactReplay(concurrent, event, fingerprint, idempotencyKey);
            return concurrent;
        }

        if (status == RefundStatus.REQUESTED) {
            outboxService.enqueue(refundCase);
        }
        log.info("Refund case {} created for order {} with status {}",
                refundCase.getRefundId(), refundCase.getOrderId(), status);
        return refundCase;
    }

    private RefundCase findExisting(OrderCancelledEvent event, RefundTrigger trigger, String idempotencyKey) {
        RefundCase byEvent = repository.findByEventId(event.getEventId()).orElse(null);
        if (byEvent != null) {
            return byEvent;
        }
        RefundCase byKey = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (byKey != null) {
            return byKey;
        }
        return repository.findByOrderIdAndTriggerAndComponent(
                event.getOrderId(), trigger, RefundComponent.ORDER_TOTAL).orElse(null);
    }

    private int insertIfAbsent(RefundCase refundCase) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return insertIfAbsentH2(refundCase);
        }
        return insertIfAbsentPostgres(refundCase);
    }

    private int insertIfAbsentPostgres(RefundCase refundCase) {
        return repository.insertIfAbsentPostgres(
                refundCase.getRefundId(), refundCase.getEventId(), refundCase.getIdempotencyKey(),
                refundCase.getOrderId(), refundCase.getUserId(), refundCase.getRestaurantId(),
                refundCase.getPreviousOrderStatus(), refundCase.getCurrentOrderStatus(),
                refundCase.getPaymentMethod(), refundCase.getTrigger().name(), refundCase.getComponent().name(),
                refundCase.getStatus().name(), refundCase.getCurrency(), refundCase.getSubtotalAmount(),
                refundCase.getDiscountAmount(), refundCase.getShippingFee(), refundCase.getTotalAmount(),
                refundCase.getCapturedAmount(), refundCase.getRefundAmount(), refundCase.getActorSource(),
                refundCase.getActorId(), refundCase.getReason(), refundCase.getPayloadFingerprint(),
                refundCase.getAttempts());
    }

    private int insertIfAbsentH2(RefundCase refundCase) {
        return repository.insertIfAbsentH2(
                refundCase.getRefundId(), refundCase.getEventId(), refundCase.getIdempotencyKey(),
                refundCase.getOrderId(), refundCase.getUserId(), refundCase.getRestaurantId(),
                refundCase.getPreviousOrderStatus(), refundCase.getCurrentOrderStatus(),
                refundCase.getPaymentMethod(), refundCase.getTrigger().name(), refundCase.getComponent().name(),
                refundCase.getStatus().name(), refundCase.getCurrency(), refundCase.getSubtotalAmount(),
                refundCase.getDiscountAmount(), refundCase.getShippingFee(), refundCase.getTotalAmount(),
                refundCase.getCapturedAmount(), refundCase.getRefundAmount(), refundCase.getActorSource(),
                refundCase.getActorId(), refundCase.getReason(), refundCase.getPayloadFingerprint(),
                refundCase.getAttempts());
    }

    @Transactional(readOnly = true)
    public List<RefundCaseResponse> listAdminCases(RefundStatus status, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), ADMIN_LIST_LIMIT);
        List<RefundCase> cases = status == null
                ? repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                : repository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, limit));
        return cases.stream().map(this::toAdminResponse).toList();
    }

    @Transactional(readOnly = true)
    public RefundCaseResponse getAdminCase(UUID refundId) {
        if (refundId == null) {
            throw new IllegalArgumentException("refundId is required");
        }
        return repository.findById(refundId)
                .map(this::toAdminResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Refund case", "refundId", refundId));
    }

    @Transactional(readOnly = true)
    public List<RefundCustomerCaseResponse> listCustomerCases(Long userId, int requestedLimit) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        int limit = Math.min(Math.max(requestedLimit, 1), ADMIN_LIST_LIMIT);
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(this::toCustomerResponse)
                .toList();
    }

    private RefundCaseResponse toAdminResponse(RefundCase refundCase) {
        return RefundCaseResponse.builder()
                .refundId(refundCase.getRefundId())
                .eventId(refundCase.getEventId())
                .idempotencyKey(refundCase.getIdempotencyKey())
                .orderId(refundCase.getOrderId())
                .userId(refundCase.getUserId())
                .restaurantId(refundCase.getRestaurantId())
                .previousOrderStatus(refundCase.getPreviousOrderStatus())
                .currentOrderStatus(refundCase.getCurrentOrderStatus())
                .paymentMethod(refundCase.getPaymentMethod())
                .trigger(refundCase.getTrigger() == null ? null : refundCase.getTrigger().name())
                .component(refundCase.getComponent() == null ? null : refundCase.getComponent().name())
                .status(refundCase.getStatus() == null ? null : refundCase.getStatus().name())
                .currency(refundCase.getCurrency())
                .subtotalAmount(refundCase.getSubtotalAmount())
                .discountAmount(refundCase.getDiscountAmount())
                .shippingFee(refundCase.getShippingFee())
                .totalAmount(refundCase.getTotalAmount())
                .capturedAmount(refundCase.getCapturedAmount())
                .refundAmount(refundCase.getRefundAmount())
                .actorSource(refundCase.getActorSource())
                .actorId(refundCase.getActorId())
                .reason(refundCase.getReason())
                .providerReference(refundCase.getProviderReference())
                .lastError(refundCase.getLastError())
                .attempts(refundCase.getAttempts())
                .createdAt(refundCase.getCreatedAt())
                .updatedAt(refundCase.getUpdatedAt())
                .processedAt(refundCase.getProcessedAt())
                .build();
    }

    private RefundCustomerCaseResponse toCustomerResponse(RefundCase refundCase) {
        return RefundCustomerCaseResponse.builder()
                .refundId(refundCase.getRefundId())
                .orderId(refundCase.getOrderId())
                .paymentMethod(refundCase.getPaymentMethod())
                .trigger(refundCase.getTrigger() == null ? null : refundCase.getTrigger().name())
                .status(refundCase.getStatus() == null ? null : refundCase.getStatus().name())
                .currency(refundCase.getCurrency())
                .refundAmount(refundCase.getRefundAmount())
                .createdAt(refundCase.getCreatedAt())
                .updatedAt(refundCase.getUpdatedAt())
                .processedAt(refundCase.getProcessedAt())
                .build();
    }

    private RefundStatus decideStatus(OrderCancelledEvent event, RefundTrigger trigger) {
        if (COD.equals(event.getPaymentMethod()) && isBeforePickup(event.getPreviousStatus())) {
            return RefundStatus.NO_REFUND_REQUIRED;
        }
        if (!isAutoEligible(event, trigger)) {
            return RefundStatus.MANUAL_REVIEW;
        }
        if (ONLINE.equals(event.getPaymentMethod()) && !providerProcessingEnabled) {
            return RefundStatus.MANUAL_REVIEW;
        }
        return RefundStatus.REQUESTED;
    }

    private boolean isAutoEligible(OrderCancelledEvent event, RefundTrigger trigger) {
        if (!isBeforePickup(event.getPreviousStatus())) {
            return false;
        }
        return switch (trigger) {
            case SHIPPER_NOT_FOUND -> "SYSTEM".equals(actorSource(event));
            case PAYMENT_FAILED -> ONLINE.equals(event.getPaymentMethod())
                    && "SYSTEM".equals(actorSource(event));
            case ORDER_CANCELLED -> switch (actorSource(event)) {
                case "CUSTOMER" -> "PENDING".equals(event.getPreviousStatus())
                        && "CUSTOMER_CANCELLED".equals(event.getCancelReasonCode());
                case "RESTAURANT" -> "RESTAURANT_REJECTED".equals(event.getCancelReasonCode());
                case "SYSTEM" -> "SYSTEM_CANCELLED".equals(event.getCancelReasonCode());
                default -> false;
            };
            case DELIVERY_DISPUTE -> false;
        };
    }

    private boolean isBeforePickup(String status) {
        return List.of("PENDING", "CONFIRMED", "FINDING_SHIPPER", "WAIT_SHIPPER_CONFIRM", "ASSIGNED")
                .contains(status);
    }

    private void validate(OrderCancelledEvent event) {
        if (event == null || event.getEventId() == null || event.getOrderId() == null || event.getOrderId() <= 0
                || event.getUserId() == null || event.getUserId() <= 0
                || event.getRestaurantId() == null || event.getRestaurantId() <= 0) {
            throw new IllegalArgumentException("refund cancellation event identity is required");
        }
        if (!"ORDER_CANCELLED".equals(event.getEventType())
                && !REFUND_ELIGIBLE.equals(event.getEventType())) {
            throw new IllegalArgumentException("refund event type is invalid");
        }
        if (event.getPreviousStatus() == null || event.getPreviousStatus().isBlank()
                || event.getCancelReason() == null || event.getCancelReason().isBlank()) {
            throw new IllegalArgumentException("previous status and cancellation reason are required");
        }
        if (!COD.equals(event.getPaymentMethod()) && !ONLINE.equals(event.getPaymentMethod())) {
            throw new IllegalArgumentException("refund payment method must be COD or ONLINE");
        }
        RefundTrigger trigger = resolveTrigger(event);
        if (trigger == RefundTrigger.SHIPPER_NOT_FOUND
                && !SHIPPER_NOT_FOUND.equals(event.getCurrentStatus())) {
            throw new IllegalArgumentException("shipper-not-found refund requires SHIPPER_NOT_FOUND status");
        }
        if (trigger != RefundTrigger.SHIPPER_NOT_FOUND
                && !CANCELLED.equals(event.getCurrentStatus())) {
            throw new IllegalArgumentException("refund cancellation requires CANCELLED status");
        }
        if (trigger == RefundTrigger.SHIPPER_NOT_FOUND && !"SYSTEM".equals(actorSource(event))) {
            throw new IllegalArgumentException("shipper-not-found refund must be system sourced");
        }
        if (trigger == RefundTrigger.PAYMENT_FAILED && !ONLINE.equals(event.getPaymentMethod())) {
            throw new IllegalArgumentException("payment failure refund must be ONLINE");
        }
        requireNonNegative(event.getSubtotalPrice(), "subtotalPrice");
        requireNonNegative(event.getDiscountAmount(), "discountAmount");
        requireNonNegative(event.getShippingFee(), "shippingFee");
        requirePositive(event.getTotalPrice(), "totalPrice");
        BigDecimal calculatedTotal = event.getSubtotalPrice().add(event.getShippingFee())
                .subtract(event.getDiscountAmount());
        if (calculatedTotal.compareTo(event.getTotalPrice()) != 0) {
            throw new IllegalArgumentException("order monetary snapshot does not reconcile");
        }
    }

    private RefundTrigger resolveTrigger(OrderCancelledEvent event) {
        if (REFUND_ELIGIBLE.equals(event.getEventType())
                || SHIPPER_NOT_FOUND.equals(event.getCancelReasonCode())) {
            return RefundTrigger.SHIPPER_NOT_FOUND;
        }
        if ("PAYMENT_FAILED".equals(event.getCancelReasonCode())) {
            return RefundTrigger.PAYMENT_FAILED;
        }
        return RefundTrigger.ORDER_CANCELLED;
    }

    private String actorSource(OrderCancelledEvent event) {
        if (event.getCancelledBySource() != null && !event.getCancelledBySource().isBlank()) {
            return event.getCancelledBySource().trim().toUpperCase(java.util.Locale.ROOT);
        }
        // Legacy events remain readable but are never auto-eligible when an
        // online provider is eventually enabled.
        return event.getCancelledBy() == null ? "SYSTEM" : "LEGACY_ACTOR";
    }

    private void requireExactReplay(RefundCase existing, OrderCancelledEvent event,
                                    String fingerprint, String idempotencyKey) {
        if (!Objects.equals(existing.getIdempotencyKey(), idempotencyKey)
                || !Objects.equals(existing.getOrderId(), event.getOrderId())
                || !Objects.equals(existing.getEventId(), event.getEventId())
                || !Objects.equals(existing.getPayloadFingerprint(), fingerprint)) {
            throw new IllegalArgumentException("refund event replay has a contradictory payload");
        }
    }

    private String fingerprint(OrderCancelledEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("refund event cannot be serialized", e);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
