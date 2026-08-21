package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.RestaurantMapper;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.RestaurantService;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import com.delivery.restaurant_service.service.SearchSyncPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantServiceImpl implements RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final RestaurantCacheService restaurantCacheService;
    private final SearchSyncPublisher searchSyncPublisher;
    private final MeterRegistry meterRegistry;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Override
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request,
            Long creatorId,
            String role) {
        return createRestaurant(request, creatorId, creatorId, role);
    }

    @Override
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request,
            Long ownerPrincipalId, Long creatorId, String role) {

        if (role == null || !RoleConstants.ALLOWED_CREATORS.contains(role.toUpperCase())) {
            throw new AccessDeniedException("Only ADMIN or OWNER can create restaurants");
        }
        if (creatorId == null) {
            throw new AccessDeniedException("You must be authenticated to create a restaurant");
        }

        Restaurant restaurant = restaurantMapper.toEntity(request);
        restaurant.setCreatorId(creatorId);
        restaurant.setOwnerPrincipalId(ownerPrincipalId);

        Restaurant saved = restaurantRepository.save(restaurant);

        // ✅ Create initial balance for restaurant
        try {
            log.info("✅ Created initial balance for restaurant: {} (ID: {})", saved.getName(), saved.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to create initial balance for restaurant: {}", e.getMessage());
            // Don't fail restaurant creation if balance creation fails
        }

        // 🔥 Cache restaurant data after creation
        try {
            restaurantCacheService.cacheRestaurant(saved);
            log.info("✅ Cached new restaurant: {} (ID: {})", saved.getName(), saved.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to cache restaurant after creation: {}", e.getMessage());
        }

        // 🔥 Publish sync event for search service
        searchSyncPublisher.publishRestaurantChange(saved, "CREATE");

        return restaurantMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public RestaurantResponse updateRestaurant(Long id, UpdateRestaurantRequest request, Long creatorId, String role) {
        return updateRestaurant(id, request, creatorId, creatorId, role);
    }

    @Override
    @Transactional
    public RestaurantResponse updateRestaurant(Long id, UpdateRestaurantRequest request,
            Long ownerPrincipalId, Long creatorId, String role) {
        Restaurant existingRestaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        if (!canManage(existingRestaurant, ownerPrincipalId, creatorId, role)) {
            throw new AccessDeniedException("You are not allowed to update this restaurant");
        }

        restaurantMapper.updateEntityFromDto(request, existingRestaurant);
        Restaurant updated = restaurantRepository.save(existingRestaurant);

        // 🔥 Update cache after modification
        try {
            restaurantCacheService.cacheRestaurant(updated);
            log.info("🔄 Updated cache for restaurant: {} (ID: {})", updated.getName(), updated.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to update cache after restaurant update: {}", e.getMessage());
        }

        // 🔥 Publish sync event for search service
        searchSyncPublisher.publishRestaurantChange(updated, "UPDATE");

        return restaurantMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteRestaurant(Long id, Long creatorId, String role) {
        deleteRestaurant(id, creatorId, creatorId, role);
    }

    @Override
    @Transactional
    public void deleteRestaurant(Long id, Long ownerPrincipalId, Long creatorId, String role) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        if (!canManage(restaurant, ownerPrincipalId, creatorId, role)) {
            throw new AccessDeniedException("You are not allowed to delete this restaurant");
        }

        // 🔥 Remove from cache before deletion
        try {
            restaurantCacheService.removeRestaurantFromCache(id);
            log.info("🗑️ Removed restaurant from cache: {} (ID: {})", restaurant.getName(), id);
        } catch (Exception e) {
            log.warn("⚠️ Failed to remove restaurant from cache: {}", e.getMessage());
        }

        // 🔥 Publish sync event for search service
        searchSyncPublisher.publishRestaurantChange(restaurant, "DELETE");

        restaurantRepository.deleteById(id);
    }

    @Override
    public RestaurantResponse getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Not found"));
        return restaurantMapper.toResponse(restaurant);
    }

    @Override
    public List<RestaurantResponse> getAllRestaurants() {
        List<Restaurant> list = restaurantRepository.findAll(PageRequest.of(0, 100)).getContent();
        return list.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    private boolean canManage(Restaurant restaurant, Long ownerPrincipalId, Long creatorId, String role) {
        if (RoleConstants.ADMIN.equals(role)) return true;
        if (!RoleConstants.OWNER.equals(role)) return false;
        if (restaurant.getOwnerPrincipalId() != null) {
            return ownerPrincipalId != null && ownerPrincipalId.equals(restaurant.getOwnerPrincipalId());
        }
        if (principalOwnershipEnforced) return false;
        boolean legacyMatch = creatorId != null && creatorId.equals(restaurant.getCreatorId());
        if (legacyMatch) identityLegacyFallback("owner_manage");
        return legacyMatch;
    }

    @Override
    public List<RestaurantResponse> findByName(String keyword) {

        List<Restaurant> restaurants = restaurantRepository.findByNameContainingIgnoreCase(
                keyword, PageRequest.of(0, 100));
        return restaurants.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> getRestaurantsByCreatorId(Long creatorId) {
        List<Restaurant> restaurants = restaurantRepository.findByCreatorId(
                creatorId, PageRequest.of(0, 100)).getContent();
        return restaurants.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> getRestaurantsByOwnerPrincipalId(Long ownerPrincipalId, Long legacyCreatorId) {
        var restaurants = principalOwnershipEnforced
                ? restaurantRepository.findByOwnerPrincipalId(ownerPrincipalId, PageRequest.of(0, 100)).getContent()
                : restaurantRepository.findByOwnerPrincipalOrUnmigratedCreator(
                        ownerPrincipalId, legacyCreatorId, PageRequest.of(0, 100)).getContent();
        if (!principalOwnershipEnforced) {
            restaurants.stream().filter(restaurant -> restaurant.getOwnerPrincipalId() == null)
                    .forEach(restaurant -> identityLegacyFallback("owner_list"));
        }
        return restaurants.stream()
                .map(restaurantMapper::toResponse).collect(Collectors.toList());
    }

    private void identityLegacyFallback(String surface) {
        Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "restaurant").tag("surface", surface)
                .register(meterRegistry).increment();
    }

}
