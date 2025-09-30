package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.MenuItem;

import java.util.Map;

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
    
    // ===============================
    // GETTER METHODS FOR VALIDATION
    // ===============================
    
    /**
     * Get restaurant from cache để validation
     */
    Map<String, Object> getRestaurantFromCache(Long restaurantId);
    
    /**
     * Get menu item from cache để validation
     */
    Map<String, Object> getMenuItemFromCache(Long menuItemId);
    
    /**
     * Check xem restaurant có tồn tại trong cache không
     */
    boolean isRestaurantInCache(Long restaurantId);
    
    /**
     * Check xem menu item có tồn tại trong cache không
     */
    boolean isMenuItemInCache(Long menuItemId);
    
    /**
     * Get menu item price từ cache
     */
    Double getMenuItemPrice(Long menuItemId);
    
    /**
     * Check restaurant availability (combining isAvailable and operating hours)
     */
    boolean isRestaurantAvailable(Long restaurantId);
    
    /**
     * Check menu item availability và stock
     */
    boolean isMenuItemAvailable(Long restaurantId, Long menuItemId, Integer quantity);
}
