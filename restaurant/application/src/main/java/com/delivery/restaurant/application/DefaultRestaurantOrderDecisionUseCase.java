package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.decision.*;

/** One authoritative decision per order, with payload-aware replay validation. */
public final class DefaultRestaurantOrderDecisionUseCase implements RestaurantOrderDecisionUseCase {
    private final RestaurantDecisionStorePort decisions;
    private final OrderDecisionEligibilityPort orders;
    private final RestaurantTransactionPort transactions;
    public DefaultRestaurantOrderDecisionUseCase(RestaurantDecisionStorePort decisions,
            OrderDecisionEligibilityPort orders, RestaurantTransactionPort transactions) {
        this.decisions = decisions; this.orders = orders; this.transactions = transactions;
    }
    @Override public void confirm(Long order, Long restaurant, Long actor, Integer prepTime, String notes) {
        positive(order, "orderId"); positive(restaurant, "restaurantId"); positive(actor, "actorUserId");
        if (prepTime == null || prepTime <= 0 || prepTime > 240)
            throw new IllegalArgumentException("estimatedPrepTime must be between 1 and 240");
        decide(new RestaurantDecisionCommand(order, restaurant, actor, RestaurantDecisionKind.CONFIRMED, prepTime, notes, null));
    }
    @Override public void reject(Long order, Long restaurant, Long actor, String reason) {
        positive(order, "orderId"); positive(restaurant, "restaurantId"); positive(actor, "actorUserId");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("rejectionReason must not be blank");
        decide(new RestaurantDecisionCommand(order, restaurant, actor, RestaurantDecisionKind.REJECTED, null, null, reason));
    }
    private void decide(RestaurantDecisionCommand command) {
        transactions.required(() -> {
            String expected = decisions.fingerprint(command);
            decisions.lockOrder(command.orderId());
            var existing = decisions.find(command.orderId());
            if (existing.isPresent()) {
                var stored = existing.get();
                if (!stored.restaurantId().equals(command.restaurantId()) || stored.decision() != command.decision())
                    throw new RestaurantDecisionConflictException("Order " + command.orderId() + " already has decision " + stored.decision());
                String actual = stored.fingerprint() != null ? stored.fingerprint()
                        : decisions.legacyFingerprint(command.orderId(), command.decision()).orElse(null);
                if (actual != null && !actual.equals(expected))
                    throw new RestaurantDecisionConflictException("Restaurant decision replay has a contradictory payload");
                return null;
            }
            orders.requirePendingOrderForRestaurant(command.orderId(), command.restaurantId());
            decisions.insertDecisionAndEvent(command, expected);
            return null;
        });
    }
    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
