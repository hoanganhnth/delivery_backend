package com.delivery.restaurant.infrastructure.client;

/**
 * Application-facing eligibility boundary for rating a delivered order.
 *
 * <p>The port deliberately exposes no HTTP types or transport configuration.
 */
public interface OrderEligibilityPort {

    void requireDeliveredOrder(Long orderId, Long userId, Long restaurantId);
}
