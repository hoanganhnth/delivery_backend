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
import com.delivery.restaurant_service.service.RestaurantCatalogService;
import com.delivery.restaurant_service.service.RestaurantBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantServiceImpl implements RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final RestaurantCacheService restaurantCacheService;
    private final RestaurantCatalogService restaurantCatalogService;
    private final RestaurantBalanceService restaurantBalanceService;

    @Override
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request,
            Long creatorId,
            String role) {

        if (role == null || !RoleConstants.ALLOWED_CREATORS.contains(role.toUpperCase())) {
            throw new AccessDeniedException("Only ADMIN or OWNER can create restaurants");
        }
        if (creatorId == null) {
            throw new AccessDeniedException("You must be authenticated to create a restaurant");
        }

        Restaurant restaurant = restaurantMapper.toEntity(request);
        restaurant.setCreatorId(creatorId);

        Restaurant saved = restaurantRepository.save(restaurant);

        // ✅ Create initial balance for restaurant
        try {
            restaurantBalanceService.createInitialBalance(saved);
            log.info("✅ Created initial balance for restaurant: {} (ID: {})", saved.getName(), saved.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to create initial balance for restaurant: {}", e.getMessage());
            // Don't fail restaurant creation if balance creation fails
        }

        // 🔥 Cache restaurant data after creation
        try {
            restaurantCacheService.cacheRestaurant(saved);
            restaurantCatalogService.cacheRestaurantForHomeFeed(saved, Collections.emptyList());
            log.info("✅ Cached new restaurant: {} (ID: {})", saved.getName(), saved.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to cache restaurant after creation: {}", e.getMessage());
        }

        return restaurantMapper.toResponse(saved);
    }

    @Override
    public RestaurantResponse updateRestaurant(Long id, UpdateRestaurantRequest request, Long creatorId) {
        Restaurant existingRestaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        if (!existingRestaurant.getCreatorId().equals(creatorId)) {
            throw new AccessDeniedException("You are not allowed to update this restaurant");
        }

        restaurantMapper.updateEntityFromDto(request, existingRestaurant);
        Restaurant updated = restaurantRepository.save(existingRestaurant);

        // 🔥 Update cache after modification
        try {
            restaurantCacheService.cacheRestaurant(updated);
            restaurantCatalogService.cacheRestaurantForHomeFeed(updated, Collections.emptyList());
            log.info("🔄 Updated cache for restaurant: {} (ID: {})", updated.getName(), updated.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to update cache after restaurant update: {}", e.getMessage());
        }

        return restaurantMapper.toResponse(updated);
    }

    @Override
    public void deleteRestaurant(Long id, Long creatorId) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        if (!restaurant.getCreatorId().equals(creatorId)) {
            throw new AccessDeniedException("You are not allowed to delete this restaurant");
        }

        // 🔥 Remove from cache before deletion
        try {
            restaurantCacheService.removeRestaurantFromCache(id);
            restaurantCatalogService.removeRestaurantFromCatalog(id);
            log.info("🗑️ Removed restaurant from cache: {} (ID: {})", restaurant.getName(), id);
        } catch (Exception e) {
            log.warn("⚠️ Failed to remove restaurant from cache: {}", e.getMessage());
        }

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
        List<Restaurant> list = restaurantRepository.findAll();
        return list.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> findByName(String keyword) {

        List<Restaurant> restaurants = restaurantRepository.findByNameContainingIgnoreCase(keyword);
        return restaurants.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> getRestaurantsByCreatorId(Long creatorId) {
        List<Restaurant> restaurants = restaurantRepository.findByCreatorId(creatorId);
        return restaurants.stream()
                .map(restaurantMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update restaurant availability và cache
     */
    public void updateRestaurantAvailability(Long restaurantId, boolean isAvailable, Long creatorId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        if (!restaurant.getCreatorId().equals(creatorId)) {
            throw new AccessDeniedException("You are not allowed to update this restaurant availability");
        }

        // 🔥 Update cache availability
        try {
            restaurantCacheService.updateRestaurantAvailability(restaurantId, isAvailable);
            restaurantCatalogService.updateRestaurantAvailability(restaurantId, isAvailable, true);
            log.info("🔄 Updated availability for restaurant: {} -> {}", restaurant.getName(), isAvailable);
        } catch (Exception e) {
            log.warn("⚠️ Failed to update restaurant availability in cache: {}", e.getMessage());
        }
    }
}
