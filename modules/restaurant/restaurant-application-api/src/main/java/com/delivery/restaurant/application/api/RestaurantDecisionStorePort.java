package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.decision.RestaurantDecisionKind;
import java.util.Optional;

public interface RestaurantDecisionStorePort {
    void lockOrder(Long orderId);
    Optional<StoredDecision> find(Long orderId);
    Optional<String> legacyFingerprint(Long orderId, RestaurantDecisionKind decision);
    String fingerprint(RestaurantDecisionCommand command);
    void insertDecisionAndEvent(RestaurantDecisionCommand command, String fingerprint);
    record StoredDecision(Long restaurantId, RestaurantDecisionKind decision, String fingerprint) {}
}
