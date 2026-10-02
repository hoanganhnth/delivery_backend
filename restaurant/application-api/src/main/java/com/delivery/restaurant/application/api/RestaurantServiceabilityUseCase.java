package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.util.List;

/** Port for restaurant-owned serviceability configuration and evaluation. */
public interface RestaurantServiceabilityUseCase {

    List<ServiceabilityZoneResult> list(Long restaurantId, Long principalId, Long legacyUserId,
            RestaurantActorRole actorRole);

    ServiceabilityZoneResult create(Long restaurantId, CreateServiceabilityZoneCommand command,
            Long principalId, Long legacyUserId, RestaurantActorRole actorRole);

    ServiceabilityZoneResult update(Long restaurantId, Long zoneId, UpdateServiceabilityZoneCommand command,
            Long principalId, Long legacyUserId, RestaurantActorRole actorRole);

    void delete(Long restaurantId, Long zoneId, Long principalId, Long legacyUserId,
            RestaurantActorRole actorRole);

    ServiceabilityDecision evaluate(Long restaurantId, Double latitude, Double longitude);
}
