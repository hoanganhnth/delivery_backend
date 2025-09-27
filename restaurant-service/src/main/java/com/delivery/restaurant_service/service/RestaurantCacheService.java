package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.MenuItem;

/**
 * Service để cache restaurant và menu data vào Redis
 * Thiết kế để tương thích với future Catalog Service
 */
public interface RestaurantCacheService {
    
    /**
     * Cache restaurant info vào Redis
     */
    void cacheRestaurant(Restaurant restaurant);
    
    /**
     * Cache menu item vào Redis
     */
    void cacheMenuItem(MenuItem menuItem);
    
    /**
     * Remove restaurant khỏi cache
     */
    void removeRestaurantFromCache(Long restaurantId);
    
    /**
     * Remove menu item khỏi cache
     */
    void removeMenuItemFromCache(Long menuItemId);
    
    /**
     * Update restaurant availability trong cache
     */
    void updateRestaurantAvailability(Long restaurantId, boolean isAvailable);
    
    /**
     * Update menu item availability trong cache
     */
    void updateMenuItemAvailability(Long menuItemId, boolean isAvailable);
}
