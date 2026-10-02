package com.delivery.restaurant.infrastructure.serviceability;

import com.delivery.restaurant.application.api.ServiceabilityStorePort;
import com.delivery.restaurant.application.api.ServiceabilityZoneResult;
import com.delivery.restaurant.domain.serviceability.ServiceabilityResourceNotFoundException;
import com.delivery.restaurant_service.entity.RestaurantServiceabilityZone;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.repository.RestaurantServiceabilityZoneRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Maps JPA state to persistence facts within the use case's transaction. */
@Component
@RequiredArgsConstructor
public class JpaServiceabilityAdapter implements ServiceabilityStorePort {
    private final RestaurantRepository restaurants;
    private final RestaurantServiceabilityZoneRepository zones;

    @Override public Optional<RestaurantOwner> findRestaurant(Long id) {
        return restaurants.findById(id).map(r -> new RestaurantOwner(r.getOwnerPrincipalId(), r.getCreatorId()));
    }
    @Override public boolean restaurantExists(Long id) { return restaurants.existsById(id); }
    @Override public List<ServiceabilityZoneResult> orderedZones(Long id) {
        return zones.findByRestaurantIdOrderByPriorityDescIdAsc(id).stream().map(JpaServiceabilityAdapter::toResult).toList();
    }
    @Override public Optional<ServiceabilityZoneResult> findZone(Long id) {
        return zones.findById(id).map(JpaServiceabilityAdapter::toResult);
    }
    @Override public ServiceabilityZoneResult save(ServiceabilityZoneResult value) {
        RestaurantServiceabilityZone zone = value.id() == null ? new RestaurantServiceabilityZone() : requiredZone(value.id());
        zone.setRestaurantId(value.restaurantId());
        zone.setName(value.name());
        zone.setPolygonGeoJson(value.polygonGeoJson());
        zone.setPriority(value.priority());
        zone.setActive(value.active());
        return toResult(zones.save(zone));
    }
    @Override public void delete(Long id) { zones.delete(requiredZone(id)); }

    private RestaurantServiceabilityZone requiredZone(Long id) {
        return zones.findById(id).orElseThrow(() -> new ServiceabilityResourceNotFoundException("Serviceability zone not found"));
    }
    private static ServiceabilityZoneResult toResult(RestaurantServiceabilityZone zone) {
        return new ServiceabilityZoneResult(zone.getId(), zone.getRestaurantId(), zone.getName(),
                zone.getPolygonGeoJson(), zone.getPriority(), zone.isActive(), zone.getRevision(),
                zone.getCreatedAt(), zone.getUpdatedAt());
    }
}
