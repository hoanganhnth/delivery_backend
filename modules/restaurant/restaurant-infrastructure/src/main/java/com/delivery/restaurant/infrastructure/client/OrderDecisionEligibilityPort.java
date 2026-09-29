package com.delivery.restaurant.infrastructure.client;

/**
 * Application-facing eligibility boundary for accepting or rejecting an order.
 *
 * <p>The port deliberately exposes no HTTP types or transport configuration.
 */
public interface OrderDecisionEligibilityPort {

    void requirePendingOrderForRestaurant(Long orderId, Long restaurantId);
}
