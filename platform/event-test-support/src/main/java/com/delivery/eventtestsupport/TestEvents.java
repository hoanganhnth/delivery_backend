package com.delivery.eventtestsupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Small, transport-shaped records used by {@link EventFixtures}.
 *
 * <p>The records deliberately contain only stable wire fields. They do not
 * replace service-owned event DTOs and keep this test-only support module
 * independent from every business service.</p>
 */
public final class TestEvents {

    private TestEvents() {
    }

    public record OrderCreatedEvent(
            UUID eventId,
            int schemaVersion,
            Long orderId,
            Long userId,
            Long userPrincipalId,
            Long restaurantId,
            String status,
            BigDecimal subtotalPrice,
            BigDecimal discountAmount,
            BigDecimal shippingFee,
            BigDecimal totalPrice,
            String paymentMethod,
            String restaurantName,
            String restaurantAddress,
            String customerName,
            String deliveryAddress,
            Double deliveryLat,
            Double deliveryLng,
            Double pickupLat,
            Double pickupLng,
            Instant createdAt) {
    }

    public record OrderCancelledEvent(
            UUID eventId,
            int schemaVersion,
            Long orderId,
            Long userId,
            Long userPrincipalId,
            Long restaurantId,
            String previousStatus,
            String currentStatus,
            String cancelReason,
            String cancelledBySource,
            String cancelReasonCode,
            BigDecimal subtotalPrice,
            BigDecimal discountAmount,
            BigDecimal shippingFee,
            BigDecimal totalPrice,
            String paymentMethod,
            Instant cancelledAt) {
    }

    public record DeliveryStatusUpdatedEvent(
            UUID eventId,
            Long deliveryId,
            Long orderId,
            Long shipperId,
            String status,
            String previousStatus,
            Instant updatedAt,
            Double currentLat,
            Double currentLng) {
    }

    public record DeliveryCompletedEvent(
            UUID eventId,
            String eventType,
            Long deliveryId,
            Long orderId,
            Long restaurantId,
            Long shipperId,
            BigDecimal restaurantEarnings,
            BigDecimal shipperEarnings,
            BigDecimal restaurantCommission,
            BigDecimal shippingCommission,
            BigDecimal totalPlatformEarnings,
            BigDecimal shippingFee,
            BigDecimal totalPrice,
            Instant deliveredAt,
            String deliveryAddress,
            String paymentMethod,
            String restaurantName,
            String customerName) {
    }

    public record ShipperFoundEvent(
            String eventId,
            Long deliveryId,
            Long orderId,
            String matchingSessionId,
            List<ShipperMatchResult> availableShippers,
            Instant foundAt,
            String restaurantName,
            String pickupAddress,
            String deliveryAddress) {
    }

    public record ShipperMatchResult(
            Long shipperId,
            String shipperName,
            String shipperPhone,
            Double distanceKm,
            Double latitude,
            Double longitude,
            Double rating,
            Boolean isOnline) {
    }

    public record ShipperNotFoundEvent(
            String eventId,
            Long deliveryId,
            Long orderId,
            String matchingSessionId,
            String reason,
            Instant occurredAt,
            Integer retryAttempts,
            Double pickupLat,
            Double pickupLng,
            Double deliveryLat,
            Double deliveryLng) {
    }

    public record ShipperLocationUpdatedEvent(
            UUID eventId,
            Long shipperId,
            Long deliveryId,
            Double latitude,
            Double longitude,
            Boolean isOnline,
            long timestamp,
            Double accuracy,
            Double speed,
            Double heading,
            String source) {
    }

    public record IdentityStatusChangedEvent(
            UUID eventId,
            String eventType,
            int schemaVersion,
            Instant occurredAt,
            Long principalId,
            String status,
            long lifecycleVersion,
            String reasonCode,
            Long changedByPrincipalId) {
    }

    public record RestaurantOrderDecisionEvent(
            UUID eventId,
            String eventType,
            Instant occurredAt,
            Long orderId,
            Long restaurantId,
            Long actorUserId,
            String status,
            String action,
            Integer estimatedPrepTime,
            String rejectionReason,
            String notes) {
    }

    public record PaymentEvent(
            Long paymentId,
            Long orderId,
            Long userId,
            String status,
            BigDecimal amount,
            String paymentMethod,
            String transactionId,
            Instant processedAt,
            String failureReason) {
    }

    public record DeliveryExceptionReportedEvent(
            UUID eventId,
            String eventType,
            Instant occurredAt,
            UUID exceptionId,
            Long deliveryId,
            Long orderId,
            Long userId,
            Long restaurantId,
            Long shipperId,
            String previousDeliveryStatus,
            String currentDeliveryStatus,
            String exceptionStatus,
            String reason,
            BigDecimal totalPrice) {
    }
}
