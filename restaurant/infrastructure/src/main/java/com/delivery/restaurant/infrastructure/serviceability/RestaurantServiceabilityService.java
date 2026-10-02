package com.delivery.restaurant.infrastructure.serviceability;

import com.delivery.restaurant.application.api.CreateServiceabilityZoneCommand;
import com.delivery.restaurant.application.api.RestaurantServiceabilityUseCase;
import com.delivery.restaurant.application.api.ServiceabilityDecision;
import com.delivery.restaurant.application.api.ServiceabilityZoneResult;
import com.delivery.restaurant.application.api.UpdateServiceabilityZoneCommand;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.serviceability.ServiceabilityGeometry;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantServiceabilityZone;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.repository.RestaurantServiceabilityZoneRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional JPA adapter for serviceability configuration and evaluation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantServiceabilityService implements RestaurantServiceabilityUseCase {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantServiceabilityZoneRepository zoneRepository;

    @Value("${app.restaurant.serviceability-enabled:false}")
    private boolean enabled;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceabilityZoneResult> list(Long restaurantId, Long principalId, Long legacyUserId,
            RestaurantActorRole actorRole) {
        requireManageAccess(restaurantId, principalId, legacyUserId, actorRole);
        return zoneRepository.findByRestaurantIdOrderByPriorityDescIdAsc(restaurantId).stream()
                .map(RestaurantServiceabilityService::toResult).toList();
    }

    @Override
    @Transactional
    public ServiceabilityZoneResult create(Long restaurantId, CreateServiceabilityZoneCommand command,
            Long principalId, Long legacyUserId, RestaurantActorRole actorRole) {
        requireManageAccess(restaurantId, principalId, legacyUserId, actorRole);
        if (command == null) throw new IllegalArgumentException("Zone request is required");
        GeoJsonServiceabilityPolygonAdapter.parsePolygon(command.polygonGeoJson());

        RestaurantServiceabilityZone zone = new RestaurantServiceabilityZone();
        zone.setRestaurantId(restaurantId);
        zone.setName(command.name().trim());
        zone.setPolygonGeoJson(command.polygonGeoJson().trim());
        zone.setPriority(command.priority() == null ? 0 : command.priority());
        zone.setActive(command.active() == null || command.active());
        return toResult(zoneRepository.save(zone));
    }

    @Override
    @Transactional
    public ServiceabilityZoneResult update(Long restaurantId, Long zoneId,
            UpdateServiceabilityZoneCommand command, Long principalId, Long legacyUserId,
            RestaurantActorRole actorRole) {
        requireManageAccess(restaurantId, principalId, legacyUserId, actorRole);
        if (command == null) throw new IllegalArgumentException("Zone request is required");
        RestaurantServiceabilityZone zone = findOwnedZone(restaurantId, zoneId);
        if (command.revision() == null || !command.revision().equals(zone.getRevision())) {
            throw new ServiceabilityZoneConflictException("Serviceability zone revision is stale");
        }
        if (command.name() != null) {
            if (command.name().isBlank()) throw new IllegalArgumentException("Zone name is required");
            zone.setName(command.name().trim());
        }
        if (command.polygonGeoJson() != null) {
            GeoJsonServiceabilityPolygonAdapter.parsePolygon(command.polygonGeoJson());
            zone.setPolygonGeoJson(command.polygonGeoJson().trim());
        }
        if (command.priority() != null) zone.setPriority(command.priority());
        if (command.active() != null) zone.setActive(command.active());
        return toResult(zoneRepository.save(zone));
    }

    @Override
    @Transactional
    public void delete(Long restaurantId, Long zoneId, Long principalId, Long legacyUserId,
            RestaurantActorRole actorRole) {
        requireManageAccess(restaurantId, principalId, legacyUserId, actorRole);
        zoneRepository.delete(findOwnedZone(restaurantId, zoneId));
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceabilityDecision evaluate(Long restaurantId, Double latitude, Double longitude) {
        if (!enabled) return new ServiceabilityDecision(false, false, null, null, "CAPABILITY_DISABLED");
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return unavailable("INVALID_DELIVERY_COORDINATE");
        }
        try {
            ServiceabilityGeometry.requireVietnamCoordinate(longitude, latitude, "delivery coordinate");
        } catch (IllegalArgumentException invalid) {
            return unavailable("INVALID_DELIVERY_COORDINATE");
        }
        if (!restaurantRepository.existsById(restaurantId)) {
            return unavailable("RESTAURANT_NOT_FOUND");
        }

        List<RestaurantServiceabilityZone> zones = zoneRepository
                .findByRestaurantIdOrderByPriorityDescIdAsc(restaurantId);
        if (zones.isEmpty()) return unavailable("NO_ACTIVE_ZONE");
        for (RestaurantServiceabilityZone zone : zones) {
            if (!zone.isActive()) continue;
            try {
                if (ServiceabilityGeometry.contains(
                        GeoJsonServiceabilityPolygonAdapter.parsePolygon(zone.getPolygonGeoJson()),
                        longitude, latitude)) {
                    return new ServiceabilityDecision(true, true, zone.getId(), zone.getRevision(), "MATCHED_ZONE");
                }
            } catch (IllegalArgumentException invalidZone) {
                log.error("Invalid serviceability zone {} for restaurant {}", zone.getId(), restaurantId);
                return unavailable("INVALID_ZONE_CONFIGURATION");
            }
        }
        return unavailable("OUTSIDE_ACTIVE_ZONES");
    }

    private ServiceabilityDecision unavailable(String reason) {
        return new ServiceabilityDecision(true, false, null, null, reason);
    }

    private void requireManageAccess(Long restaurantId, Long principalId, Long legacyUserId,
            RestaurantActorRole actorRole) {
        if (restaurantId == null || principalId == null || legacyUserId == null) {
            throw new AccessDeniedException("Authenticated owner identity is required");
        }
        if (RestaurantActorRole.ADMIN.equals(actorRole)) return;
        if (!RestaurantActorRole.SHOP_OWNER.equals(actorRole)) {
            throw new AccessDeniedException("Only ADMIN or SHOP_OWNER may manage serviceability zones");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ServiceabilityResourceNotFoundException("Restaurant not found"));
        if (restaurant.getOwnerPrincipalId() != null) {
            if (!principalId.equals(restaurant.getOwnerPrincipalId())) {
                throw new AccessDeniedException("You are not allowed to manage this restaurant");
            }
            return;
        }
        if (principalOwnershipEnforced || !legacyUserId.equals(restaurant.getCreatorId())) {
            throw new AccessDeniedException("Restaurant ownership projection is not ready");
        }
    }

    private RestaurantServiceabilityZone findOwnedZone(Long restaurantId, Long zoneId) {
        RestaurantServiceabilityZone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ServiceabilityResourceNotFoundException("Serviceability zone not found"));
        if (!restaurantId.equals(zone.getRestaurantId())) {
            throw new AccessDeniedException("Serviceability zone belongs to another restaurant");
        }
        return zone;
    }

    private static ServiceabilityZoneResult toResult(RestaurantServiceabilityZone zone) {
        return new ServiceabilityZoneResult(zone.getId(), zone.getRestaurantId(), zone.getName(),
                zone.getPolygonGeoJson(), zone.getPriority(), zone.isActive(), zone.getRevision(),
                zone.getCreatedAt(), zone.getUpdatedAt());
    }
}
