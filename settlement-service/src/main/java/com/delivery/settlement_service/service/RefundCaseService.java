package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.event.OrderCancelledEvent;
import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.entity.RefundCase.RefundComponent;
import com.delivery.settlement_service.entity.RefundCase.RefundStatus;
import com.delivery.settlement_service.entity.RefundCase.RefundTrigger;
import com.delivery.settlement_service.repository.RefundCaseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String COD = "COD";
    private static final String ONLINE = "ONLINE";
    private static final String CANCELLED = "CANCELLED";

    private final RefundCaseRepository repository;
    private final RefundOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final boolean providerProcessingEnabled;

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
        String idempotencyKey = event.getOrderId() + ":ORDER_CANCELLED:ORDER_TOTAL";

        RefundCase byEvent = repository.findByEventId(event.getEventId()).orElse(null);
        if (byEvent != null) {
            requireExactReplay(byEvent, event, fingerprint, idempotencyKey);
            return byEvent;
        }

        RefundCase byKey = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (byKey != null) {
            requireExactReplay(byKey, event, fingerprint, idempotencyKey);
            return byKey;
        }

        RefundCase byOrder = repository.findByOrderIdAndTriggerAndComponent(
                event.getOrderId(), RefundTrigger.ORDER_CANCELLED, RefundComponent.ORDER_TOTAL).orElse(null);
        if (byOrder != null) {
            throw new IllegalArgumentException("order already has a different refund cancellation event");
        }

        RefundStatus status = decideStatus(event);
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
                .trigger(RefundTrigger.ORDER_CANCELLED)
                .component(RefundComponent.ORDER_TOTAL)
                .status(status)
                .currency("VND")
                .subtotalAmount(event.getSubtotalPrice())
                .discountAmount(event.getDiscountAmount())
                .shippingFee(event.getShippingFee())
                .totalAmount(event.getTotalPrice())
                .capturedAmount(capturedAmount)
                .refundAmount(refundAmount)
                .actorSource(event.getCancelledBy() == null ? "SYSTEM" : "ACTOR")
                .actorId(event.getCancelledBy())
                .reason(event.getCancelReason())
                .payloadFingerprint(fingerprint)
                .attempts(0)
                .build();

        RefundCase saved = repository.saveAndFlush(refundCase);
        if (status == RefundStatus.REQUESTED) {
            outboxService.enqueue(saved);
        }
        log.info("Refund case {} created for order {} with status {}", saved.getRefundId(), saved.getOrderId(), status);
        return saved;
    }

    private RefundStatus decideStatus(OrderCancelledEvent event) {
        if (COD.equals(event.getPaymentMethod()) && isBeforePickup(event.getPreviousStatus())) {
            return RefundStatus.NO_REFUND_REQUIRED;
        }
        if (!isBeforePickup(event.getPreviousStatus())) {
            return RefundStatus.MANUAL_REVIEW;
        }
        if (ONLINE.equals(event.getPaymentMethod()) && !providerProcessingEnabled) {
            return RefundStatus.MANUAL_REVIEW;
        }
        return RefundStatus.REQUESTED;
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
        if (!"ORDER_CANCELLED".equals(event.getEventType()) || !CANCELLED.equals(event.getCurrentStatus())) {
            throw new IllegalArgumentException("refund cancellation event type/status is invalid");
        }
        if (event.getPreviousStatus() == null || event.getPreviousStatus().isBlank()
                || event.getCancelReason() == null || event.getCancelReason().isBlank()) {
            throw new IllegalArgumentException("previous status and cancellation reason are required");
        }
        if (!COD.equals(event.getPaymentMethod()) && !ONLINE.equals(event.getPaymentMethod())) {
            throw new IllegalArgumentException("refund payment method must be COD or ONLINE");
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
