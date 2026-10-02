package com.delivery.restaurant.application.api;

import java.util.List;
import java.util.Optional;

/** Persistence facts only; management and evaluation decisions belong to the use case. */
public interface ServiceabilityStorePort {
    record RestaurantOwner(Long principalId, Long legacyUserId) { }
    Optional<RestaurantOwner> findRestaurant(Long restaurantId);
    boolean restaurantExists(Long restaurantId);
    List<ServiceabilityZoneResult> orderedZones(Long restaurantId);
    Optional<ServiceabilityZoneResult> findZone(Long zoneId);
    ServiceabilityZoneResult save(ServiceabilityZoneResult zone);
    void delete(Long zoneId);
}
