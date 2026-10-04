package com.delivery.settlement.domain.refund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Canonical refund eligibility and immutable event-snapshot validation. */
public final class RefundPolicy {
    private RefundPolicy() {}
    private static final String COD = "COD", ONLINE = "ONLINE", CANCELLED = "CANCELLED",
            SHIPPER_NOT_FOUND = "SHIPPER_NOT_FOUND", REFUND_ELIGIBLE = "REFUND_ELIGIBLE";
    public enum Trigger { ORDER_CANCELLED, PAYMENT_FAILED, SHIPPER_NOT_FOUND, DELIVERY_DISPUTE }
    public enum Status { REQUESTED, PROCESSING, SUCCEEDED, PARTIAL, FAILED, MANUAL_REVIEW, NO_REFUND_REQUIRED }
    public record Cancellation(UUID eventId, String eventType, Long orderId, Long userId, Long userPrincipalId,
            Long restaurantId, String previousStatus, String currentStatus, String cancelReason, Long cancelledBy,
            String cancelledBySource, String cancelReasonCode, String paymentMethod, BigDecimal subtotalPrice,
            BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal totalPrice) {}
    public record DeliveryException(UUID eventId, String eventType, UUID exceptionId, Long deliveryId,
            Long orderId, Long userId, Long userPrincipalId, Long restaurantId, Long shipperId,
            String previousDeliveryStatus, String currentDeliveryStatus, String exceptionStatus, String reason,
            String paymentMethod, BigDecimal subtotalPrice, BigDecimal discountAmount, BigDecimal shippingFee,
            BigDecimal totalPrice) {}
    public record Decision(Trigger trigger, Status status, BigDecimal capturedAmount, BigDecimal refundAmount,
            String actorSource) {}
    public static Decision decide(Cancellation event, boolean providerProcessingEnabled) {
        Trigger trigger = resolveTrigger(event);
        Status status = decideStatus(event, trigger, providerProcessingEnabled);
        BigDecimal captured = COD.equals(event.paymentMethod()) ? BigDecimal.ZERO : event.totalPrice();
        BigDecimal refund = status == Status.NO_REFUND_REQUIRED ? BigDecimal.ZERO : captured;
        return new Decision(trigger, status, captured, refund, actorSource(event));
    }
    public static Decision decide(DeliveryException event) {
        BigDecimal captured = ONLINE.equals(event.paymentMethod()) ? event.totalPrice() : BigDecimal.ZERO;
        return new Decision(Trigger.DELIVERY_DISPUTE, Status.MANUAL_REVIEW, captured, captured, "SHIPPER");
    }

    public static Status decideStatus(Cancellation event, Trigger trigger, boolean providerProcessingEnabled) {
        if (COD.equals(event.paymentMethod()) && isBeforePickup(event.previousStatus())) {
            return Status.NO_REFUND_REQUIRED;
        }
        if (!isAutoEligible(event, trigger)) {
            return Status.MANUAL_REVIEW;
        }
        if (ONLINE.equals(event.paymentMethod()) && !providerProcessingEnabled) {
            return Status.MANUAL_REVIEW;
        }
        return Status.REQUESTED;
    }

    private static boolean isAutoEligible(Cancellation event, Trigger trigger) {
        if (!isBeforePickup(event.previousStatus())) {
            return false;
        }
        return switch (trigger) {
            case SHIPPER_NOT_FOUND -> "SYSTEM".equals(actorSource(event));
            case PAYMENT_FAILED -> ONLINE.equals(event.paymentMethod())
                    && "SYSTEM".equals(actorSource(event));
            case ORDER_CANCELLED -> switch (actorSource(event)) {
                case "CUSTOMER" -> "PENDING".equals(event.previousStatus())
                        && "CUSTOMER_CANCELLED".equals(event.cancelReasonCode());
                case "RESTAURANT" -> "RESTAURANT_REJECTED".equals(event.cancelReasonCode());
                case "SYSTEM" -> "SYSTEM_CANCELLED".equals(event.cancelReasonCode());
                default -> false;
            };
            case DELIVERY_DISPUTE -> false;
        };
    }

    private static boolean isBeforePickup(String status) {
        return List.of("PENDING", "CONFIRMED", "FINDING_SHIPPER", "WAIT_SHIPPER_CONFIRM", "ASSIGNED")
                .contains(status);
    }

    public static void validate(Cancellation event) {
        if (event == null || event.eventId() == null || event.orderId() == null || event.orderId() <= 0
                || event.userId() == null || event.userId() <= 0
                || event.restaurantId() == null || event.restaurantId() <= 0) {
            throw new IllegalArgumentException("refund cancellation event identity is required");
        }
        if (!"ORDER_CANCELLED".equals(event.eventType())
                && !REFUND_ELIGIBLE.equals(event.eventType())) {
            throw new IllegalArgumentException("refund event type is invalid");
        }
        if (event.previousStatus() == null || event.previousStatus().isBlank()
                || event.cancelReason() == null || event.cancelReason().isBlank()) {
            throw new IllegalArgumentException("previous status and cancellation reason are required");
        }
        if (!COD.equals(event.paymentMethod()) && !ONLINE.equals(event.paymentMethod())) {
            throw new IllegalArgumentException("refund payment method must be COD or ONLINE");
        }
        Trigger trigger = resolveTrigger(event);
        if (trigger == Trigger.SHIPPER_NOT_FOUND
                && !SHIPPER_NOT_FOUND.equals(event.currentStatus())) {
            throw new IllegalArgumentException("shipper-not-found refund requires SHIPPER_NOT_FOUND status");
        }
        if (trigger != Trigger.SHIPPER_NOT_FOUND
                && !CANCELLED.equals(event.currentStatus())) {
            throw new IllegalArgumentException("refund cancellation requires CANCELLED status");
        }
        if (trigger == Trigger.SHIPPER_NOT_FOUND && !"SYSTEM".equals(actorSource(event))) {
            throw new IllegalArgumentException("shipper-not-found refund must be system sourced");
        }
        if (trigger == Trigger.PAYMENT_FAILED && !ONLINE.equals(event.paymentMethod())) {
            throw new IllegalArgumentException("payment failure refund must be ONLINE");
        }
        requireNonNegative(event.subtotalPrice(), "subtotalPrice");
        requireNonNegative(event.discountAmount(), "discountAmount");
        requireNonNegative(event.shippingFee(), "shippingFee");
        requirePositive(event.totalPrice(), "totalPrice");
        BigDecimal calculatedTotal = event.subtotalPrice().add(event.shippingFee())
                .subtract(event.discountAmount());
        if (calculatedTotal.compareTo(event.totalPrice()) != 0) {
            throw new IllegalArgumentException("order monetary snapshot does not reconcile");
        }
    }

    public static void validate(DeliveryException event) {
        if (event == null || event.eventId() == null || event.exceptionId() == null
                || event.deliveryId() == null || event.deliveryId() <= 0
                || event.orderId() == null || event.orderId() <= 0
                || event.userId() == null || event.userId() <= 0
                || event.restaurantId() == null || event.restaurantId() <= 0
                || event.shipperId() == null || event.shipperId() <= 0) {
            throw new IllegalArgumentException("delivery exception refund identity is required");
        }
        if (!"DELIVERY_EXCEPTION_REPORTED".equals(event.eventType())
                || !"RETRY_AVAILABLE".equals(event.exceptionStatus())) {
            throw new IllegalArgumentException("delivery exception refund event type is invalid");
        }
        if (!isPostPickupDeliveryStatus(event.previousDeliveryStatus())
                || !Objects.equals(event.previousDeliveryStatus(), event.currentDeliveryStatus())
                || event.reason() == null || event.reason().isBlank()) {
            throw new IllegalArgumentException("delivery exception must preserve one post-pickup status and a reason");
        }
        if (!COD.equals(event.paymentMethod()) && !ONLINE.equals(event.paymentMethod())) {
            throw new IllegalArgumentException("delivery exception payment method must be COD or ONLINE");
        }
        requireNonNegative(event.subtotalPrice(), "subtotalPrice");
        requireNonNegative(event.discountAmount(), "discountAmount");
        requireNonNegative(event.shippingFee(), "shippingFee");
        requirePositive(event.totalPrice(), "totalPrice");
        BigDecimal calculatedTotal = event.subtotalPrice().add(event.shippingFee())
                .subtract(event.discountAmount());
        if (calculatedTotal.compareTo(event.totalPrice()) != 0) {
            throw new IllegalArgumentException("delivery exception monetary snapshot does not reconcile");
        }
    }

    public static Trigger resolveTrigger(Cancellation event) {
        if (REFUND_ELIGIBLE.equals(event.eventType())
                || SHIPPER_NOT_FOUND.equals(event.cancelReasonCode())) {
            return Trigger.SHIPPER_NOT_FOUND;
        }
        if ("PAYMENT_FAILED".equals(event.cancelReasonCode())) {
            return Trigger.PAYMENT_FAILED;
        }
        return Trigger.ORDER_CANCELLED;
    }

    public static String actorSource(Cancellation event) {
        if (event.cancelledBySource() != null && !event.cancelledBySource().isBlank()) {
            return event.cancelledBySource().trim().toUpperCase(java.util.Locale.ROOT);
        }
        // Legacy events remain readable but are never auto-eligible when an
        // online provider is eventually enabled.
        return event.cancelledBy() == null ? "SYSTEM" : "LEGACY_ACTOR";
    }

    private static boolean isPostPickupDeliveryStatus(String status) {
        return "PICKED_UP".equals(status) || "DELIVERING".equals(status);
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
