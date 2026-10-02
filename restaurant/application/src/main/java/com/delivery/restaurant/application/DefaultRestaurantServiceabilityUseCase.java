package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.serviceability.*;
import java.util.List;
import java.util.Objects;

/** Restaurant-owned zone management and deterministic delivery coverage decisions. */
public final class DefaultRestaurantServiceabilityUseCase implements RestaurantServiceabilityUseCase {
    private final ServiceabilityStorePort store;
    private final ServiceabilityPolygonPort polygons;
    private final ServiceabilityIncidentPort incidents;
    private final RestaurantTransactionPort transactions;
    private final boolean enabled;
    private final boolean principalOwnershipEnforced;

    public DefaultRestaurantServiceabilityUseCase(ServiceabilityStorePort store,
            ServiceabilityPolygonPort polygons, ServiceabilityIncidentPort incidents,
            RestaurantTransactionPort transactions, boolean enabled, boolean principalOwnershipEnforced) {
        this.store = Objects.requireNonNull(store);
        this.polygons = Objects.requireNonNull(polygons);
        this.incidents = Objects.requireNonNull(incidents);
        this.transactions = Objects.requireNonNull(transactions);
        this.enabled = enabled;
        this.principalOwnershipEnforced = principalOwnershipEnforced;
    }

    @Override public List<ServiceabilityZoneResult> list(Long restaurantId, Long principalId,
            Long legacyUserId, RestaurantActorRole role) {
        return transactions.readOnly(() -> {
            requireManageAccess(restaurantId, principalId, legacyUserId, role);
            return store.orderedZones(restaurantId);
        });
    }

    @Override public ServiceabilityZoneResult create(Long restaurantId, CreateServiceabilityZoneCommand command,
            Long principalId, Long legacyUserId, RestaurantActorRole role) {
        return transactions.required(() -> {
            requireManageAccess(restaurantId, principalId, legacyUserId, role);
            if (command == null) throw new IllegalArgumentException("Zone request is required");
            polygons.parse(command.polygonGeoJson());
            return store.save(new ServiceabilityZoneResult(null, restaurantId, command.name().trim(),
                    command.polygonGeoJson().trim(), command.priority() == null ? 0 : command.priority(),
                    command.active() == null || command.active(), null, null, null));
        });
    }

    @Override public ServiceabilityZoneResult update(Long restaurantId, Long zoneId,
            UpdateServiceabilityZoneCommand command, Long principalId, Long legacyUserId, RestaurantActorRole role) {
        return transactions.required(() -> {
            requireManageAccess(restaurantId, principalId, legacyUserId, role);
            if (command == null) throw new IllegalArgumentException("Zone request is required");
            var zone = findOwnedZone(restaurantId, zoneId);
            if (command.revision() == null || !command.revision().equals(zone.revision())) {
                throw new ServiceabilityZoneConflictException("Serviceability zone revision is stale");
            }
            String name = zone.name();
            if (command.name() != null) {
                if (command.name().isBlank()) throw new IllegalArgumentException("Zone name is required");
                name = command.name().trim();
            }
            String polygon = zone.polygonGeoJson();
            if (command.polygonGeoJson() != null) {
                polygons.parse(command.polygonGeoJson());
                polygon = command.polygonGeoJson().trim();
            }
            return store.save(new ServiceabilityZoneResult(zone.id(), zone.restaurantId(), name, polygon,
                    command.priority() == null ? zone.priority() : command.priority(),
                    command.active() == null ? zone.active() : command.active(), zone.revision(),
                    zone.createdAt(), zone.updatedAt()));
        });
    }

    @Override public void delete(Long restaurantId, Long zoneId, Long principalId,
            Long legacyUserId, RestaurantActorRole role) {
        transactions.required(() -> {
            requireManageAccess(restaurantId, principalId, legacyUserId, role);
            store.delete(findOwnedZone(restaurantId, zoneId).id());
            return null;
        });
    }

    @Override public ServiceabilityDecision evaluate(Long restaurantId, Double latitude, Double longitude) {
        return transactions.readOnly(() -> evaluateCoverage(restaurantId, latitude, longitude));
    }

    private ServiceabilityDecision evaluateCoverage(Long restaurantId, Double latitude, Double longitude) {
        if (!enabled) return new ServiceabilityDecision(false, false, null, null, "CAPABILITY_DISABLED");
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return unavailable("INVALID_DELIVERY_COORDINATE");
        }
        try {
            ServiceabilityGeometry.requireVietnamCoordinate(longitude, latitude, "delivery coordinate");
        } catch (IllegalArgumentException invalid) {
            return unavailable("INVALID_DELIVERY_COORDINATE");
        }
        if (!store.restaurantExists(restaurantId)) return unavailable("RESTAURANT_NOT_FOUND");
        var zones = store.orderedZones(restaurantId);
        if (zones.isEmpty()) return unavailable("NO_ACTIVE_ZONE");
        for (var zone : zones) {
            if (!zone.active()) continue;
            try {
                if (ServiceabilityGeometry.contains(polygons.parse(zone.polygonGeoJson()), longitude, latitude)) {
                    return new ServiceabilityDecision(true, true, zone.id(), zone.revision(), "MATCHED_ZONE");
                }
            } catch (IllegalArgumentException invalidZone) {
                incidents.invalidZone(zone.id(), restaurantId);
                return unavailable("INVALID_ZONE_CONFIGURATION");
            }
        }
        return unavailable("OUTSIDE_ACTIVE_ZONES");
    }

    private static ServiceabilityDecision unavailable(String reason) {
        return new ServiceabilityDecision(true, false, null, null, reason);
    }

    private void requireManageAccess(Long restaurantId, Long principalId, Long legacyUserId, RestaurantActorRole role) {
        if (restaurantId == null || principalId == null || legacyUserId == null) {
            throw new ServiceabilityAccessDeniedException("Authenticated owner identity is required");
        }
        if (RestaurantActorRole.ADMIN.equals(role)) return;
        if (!RestaurantActorRole.SHOP_OWNER.equals(role)) {
            throw new ServiceabilityAccessDeniedException("Only ADMIN or SHOP_OWNER may manage serviceability zones");
        }
        var owner = store.findRestaurant(restaurantId)
                .orElseThrow(() -> new ServiceabilityResourceNotFoundException("Restaurant not found"));
        if (owner.principalId() != null) {
            if (!principalId.equals(owner.principalId())) {
                throw new ServiceabilityAccessDeniedException("You are not allowed to manage this restaurant");
            }
            return;
        }
        if (principalOwnershipEnforced || !legacyUserId.equals(owner.legacyUserId())) {
            throw new ServiceabilityAccessDeniedException("Restaurant ownership projection is not ready");
        }
    }

    private ServiceabilityZoneResult findOwnedZone(Long restaurantId, Long zoneId) {
        var zone = store.findZone(zoneId)
                .orElseThrow(() -> new ServiceabilityResourceNotFoundException("Serviceability zone not found"));
        if (!restaurantId.equals(zone.restaurantId())) {
            throw new ServiceabilityAccessDeniedException("Serviceability zone belongs to another restaurant");
        }
        return zone;
    }
}
