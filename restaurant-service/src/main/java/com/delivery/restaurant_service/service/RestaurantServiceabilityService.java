package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateServiceabilityZoneRequest;
import com.delivery.restaurant_service.dto.request.UpdateServiceabilityZoneRequest;
import com.delivery.restaurant_service.dto.response.ServiceabilityZoneResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantServiceabilityZone;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.exception.ServiceabilityZoneConflictException;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.repository.RestaurantServiceabilityZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Owns polygon configuration and the fail-closed evaluation boundary. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantServiceabilityService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantServiceabilityZoneRepository zoneRepository;

    @Value("${app.restaurant.serviceability-enabled:false}")
    private boolean enabled;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Transactional(readOnly = true)
    public List<ServiceabilityZoneResponse> list(Long restaurantId, Long principalId,
                                                  Long legacyUserId, String role) {
        requireManageAccess(restaurantId, principalId, legacyUserId, role);
        return zoneRepository.findByRestaurantIdOrderByPriorityDescIdAsc(restaurantId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public ServiceabilityZoneResponse create(Long restaurantId, CreateServiceabilityZoneRequest request,
                                             Long principalId, Long legacyUserId, String role) {
        requireManageAccess(restaurantId, principalId, legacyUserId, role);
        if (request == null) throw new IllegalArgumentException("Zone request is required");
        ServiceabilityGeometry.parsePolygon(request.getPolygonGeoJson());

        RestaurantServiceabilityZone zone = new RestaurantServiceabilityZone();
        zone.setRestaurantId(restaurantId);
        zone.setName(request.getName().trim());
        zone.setPolygonGeoJson(request.getPolygonGeoJson().trim());
        zone.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        zone.setActive(request.getActive() == null || request.getActive());
        return toResponse(zoneRepository.save(zone));
    }

    @Transactional
    public ServiceabilityZoneResponse update(Long restaurantId, Long zoneId,
                                             UpdateServiceabilityZoneRequest request,
                                             Long principalId, Long legacyUserId, String role) {
        requireManageAccess(restaurantId, principalId, legacyUserId, role);
        if (request == null) throw new IllegalArgumentException("Zone request is required");
        RestaurantServiceabilityZone zone = findOwnedZone(restaurantId, zoneId);
        if (request.getRevision() == null || !request.getRevision().equals(zone.getRevision())) {
            throw new ServiceabilityZoneConflictException("Serviceability zone revision is stale");
        }
        if (request.getName() != null) {
            if (request.getName().isBlank()) throw new IllegalArgumentException("Zone name is required");
            zone.setName(request.getName().trim());
        }
        if (request.getPolygonGeoJson() != null) {
            ServiceabilityGeometry.parsePolygon(request.getPolygonGeoJson());
            zone.setPolygonGeoJson(request.getPolygonGeoJson().trim());
        }
        if (request.getPriority() != null) zone.setPriority(request.getPriority());
        if (request.getActive() != null) zone.setActive(request.getActive());
        return toResponse(zoneRepository.save(zone));
    }

    @Transactional
    public void delete(Long restaurantId, Long zoneId, Long principalId,
                       Long legacyUserId, String role) {
        requireManageAccess(restaurantId, principalId, legacyUserId, role);
        zoneRepository.delete(findOwnedZone(restaurantId, zoneId));
    }

    @Transactional(readOnly = true)
    public ServiceabilityDecision evaluate(Long restaurantId, Double latitude, Double longitude) {
        if (!enabled) return ServiceabilityDecision.disabled();
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return ServiceabilityDecision.unavailable("INVALID_DELIVERY_COORDINATE");
        }
        try {
            ServiceabilityGeometry.requireVietnamCoordinate(longitude, latitude, "delivery coordinate");
        } catch (IllegalArgumentException invalid) {
            return ServiceabilityDecision.unavailable("INVALID_DELIVERY_COORDINATE");
        }
        if (!restaurantRepository.existsById(restaurantId)) {
            return ServiceabilityDecision.unavailable("RESTAURANT_NOT_FOUND");
        }

        List<RestaurantServiceabilityZone> zones = zoneRepository
                .findByRestaurantIdOrderByPriorityDescIdAsc(restaurantId);
        if (zones.isEmpty()) return ServiceabilityDecision.unavailable("NO_ACTIVE_ZONE");
        for (RestaurantServiceabilityZone zone : zones) {
            if (!zone.isActive()) continue;
            try {
                if (ServiceabilityGeometry.contains(
                        ServiceabilityGeometry.parsePolygon(zone.getPolygonGeoJson()),
                        longitude, latitude)) {
                    return new ServiceabilityDecision(true, true, zone.getId(), zone.getRevision(), "MATCHED_ZONE");
                }
            } catch (IllegalArgumentException invalidZone) {
                log.error("Invalid serviceability zone {} for restaurant {}", zone.getId(), restaurantId);
                return ServiceabilityDecision.unavailable("INVALID_ZONE_CONFIGURATION");
            }
        }
        return ServiceabilityDecision.unavailable("OUTSIDE_ACTIVE_ZONES");
    }

    public void requireManageAccess(Long restaurantId, Long principalId, Long legacyUserId, String role) {
        if (restaurantId == null || principalId == null || legacyUserId == null) {
            throw new AccessDeniedException("Authenticated owner identity is required");
        }
        if (RoleConstants.ADMIN.equals(role)) return;
        if (!RoleConstants.OWNER.equals(role)) {
            throw new AccessDeniedException("Only ADMIN or SHOP_OWNER may manage serviceability zones");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
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
                .orElseThrow(() -> new ResourceNotFoundException("Serviceability zone not found"));
        if (!restaurantId.equals(zone.getRestaurantId())) {
            throw new AccessDeniedException("Serviceability zone belongs to another restaurant");
        }
        return zone;
    }

    private ServiceabilityZoneResponse toResponse(RestaurantServiceabilityZone zone) {
        ServiceabilityZoneResponse response = new ServiceabilityZoneResponse();
        response.setId(zone.getId());
        response.setRestaurantId(zone.getRestaurantId());
        response.setName(zone.getName());
        response.setPolygonGeoJson(zone.getPolygonGeoJson());
        response.setPriority(zone.getPriority());
        response.setActive(zone.isActive());
        response.setRevision(zone.getRevision());
        response.setCreatedAt(zone.getCreatedAt());
        response.setUpdatedAt(zone.getUpdatedAt());
        return response;
    }
}
