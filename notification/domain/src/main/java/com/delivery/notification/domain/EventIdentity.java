package com.delivery.notification.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Ingress identity checks, deliberately stricter than direct notification calls. */
public final class EventIdentity {
    private static final Set<String> STATUSES = Set.of(
            "PENDING", "FINDING_SHIPPER", "WAIT_SHIPPER_CONFIRM", "SHIPPER_NOT_FOUND",
            "ASSIGNED", "PICKED_UP", "DELIVERING", "DELIVERED", "RETURNING", "RETURNED", "CANCELLED");
    private EventIdentity() {}

    public record Status(UUID eventId, Long deliveryId, Long orderId, Long userId, Long principalId,
            String status, String shipperName) {}
    public record SelectedShipper(Long shipperId, Double distanceKm) {}
    public record Offer(UUID eventId, Long deliveryId, Long orderId, String restaurantName,
            String pickupAddress, String deliveryAddress, List<SelectedShipper> shippers) {}

    public static void validateStatus(UUID eventId, Long deliveryId, Long orderId, Long userId, String status) {
        if (eventId == null || !positive(deliveryId) || !positive(orderId) || !positive(userId)
                || status == null || !STATUSES.contains(status)) {
            throw new IllegalArgumentException("stable eventId, positive delivery/order/user IDs and status are required");
        }
    }

    public static void validateOffer(Offer offer) {
        if (offer == null || offer.shippers() == null || offer.shippers().size() != 1) {
            throw new IllegalArgumentException("Invalid single-shipper offer for delivery: "
                    + (offer == null ? null : offer.deliveryId()));
        }
        if (offer.eventId() == null) throw new IllegalArgumentException("Persisted shipper offer is missing eventId");
        if (!positive(offer.deliveryId()) || !positive(offer.orderId())) {
            throw new IllegalArgumentException("Persisted shipper offer requires positive delivery/order IDs");
        }
        if (!hasText(offer.restaurantName()) || !hasText(offer.pickupAddress()) || !hasText(offer.deliveryAddress())) {
            throw new IllegalArgumentException("Persisted shipper offer requires canonical restaurant and address text");
        }
        var selected = offer.shippers().get(0);
        // A null element still fails with NPE, as the original adapter did.
        if (!positive(selected.shipperId()) || selected.distanceKm() == null
                || !Double.isFinite(selected.distanceKm()) || selected.distanceKm() < 0) {
            throw new IllegalArgumentException("Persisted shipper offer has invalid shipper/distance identity");
        }
    }

    public static String shipperName(String name) { return hasText(name) ? name : null; }
    private static boolean positive(Long value) { return value != null && value > 0; }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
