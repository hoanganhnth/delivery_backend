package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.response.RestaurantCatalogResponse;
import com.delivery.restaurant_service.entity.Restaurant;

import java.util.List;

/**
 * Service cho Restaurant Catalog - tối ưu cho Home Feed
 * Cache lightweight restaurant data + featured items
 */
public interface RestaurantCatalogService {
    
    /**
     * Cache restaurant cho home feed (lightweight + featured items)
     */
    void cacheRestaurantForHomeFeed(Restaurant restaurant, List<Long> featuredMenuItemIds);
    
    /**
     * Get restaurants cho home feed (có pagination)
     */
    List<RestaurantCatalogResponse> getRestaurantsForHomeFeed(int page, int size);
    
    /**
     * Get restaurants by location (geo-based)
     */
    List<RestaurantCatalogResponse> getRestaurantsByLocation(double latitude, double longitude, double radiusKm, int limit);
    
    /**
     * Search restaurants by name/cuisine
     */
    List<RestaurantCatalogResponse> searchRestaurants(String keyword, int limit);
    
    /**
     * Get restaurant basic info
     */
    RestaurantCatalogResponse getRestaurantCatalog(Long restaurantId);
    
    /**
     * Update restaurant availability trong catalog
     */
    void updateRestaurantAvailability(Long restaurantId, boolean isAvailable, boolean isOpen);
    
    /**
     * Remove restaurant khỏi catalog
     */
    void removeRestaurantFromCatalog(Long restaurantId);
    
    /**
     * Cache all restaurants to sorted set (cho pagination)
     */
    void refreshRestaurantCatalog();
}
