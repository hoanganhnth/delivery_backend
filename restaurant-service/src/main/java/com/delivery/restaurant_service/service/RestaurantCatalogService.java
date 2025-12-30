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
     * Uses default location (HCM city center)
     */
    List<RestaurantCatalogResponse> getRestaurantsForHomeFeed(int page, int size);
    
    /**
     * Get restaurants cho home feed with custom user location (HYBRID QUERY)
     * Combines GEO proximity + rating/popularity score
     * Uses default radiusKm=5.0 and nearbyLimit=50
     */
    List<RestaurantCatalogResponse> getRestaurantsForHomeFeed(int page, int size, double userLatitude, double userLongitude);
    
    /**
     * Get restaurants cho home feed with FULL custom parameters (HYBRID QUERY)
     * Combines GEO proximity + rating/popularity score
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param userLatitude User's latitude
     * @param userLongitude User's longitude
     * @param radiusKm Search radius in kilometers (e.g., 5.0)
     * @param nearbyLimit Max number of nearby restaurants to fetch (e.g., 50)
     */
    List<RestaurantCatalogResponse> getRestaurantsForHomeFeed(
        int page, int size, 
        double userLatitude, double userLongitude, 
        double radiusKm, int nearbyLimit
    );
    
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
