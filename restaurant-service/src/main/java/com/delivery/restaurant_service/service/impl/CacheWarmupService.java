package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import com.delivery.restaurant_service.service.RestaurantCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for bulk cache operations
 * Dùng để warm up cache hoặc refresh cache periodically
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupService {
    
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantCacheService restaurantCacheService;
    private final RestaurantCatalogService restaurantCatalogService;
    
    /**
     * Warm up cache cho tất cả restaurants
     */
    public void warmupRestaurantCache() {
        try {
            List<Restaurant> restaurants = restaurantRepository.findAll();
            int cached = 0;
            
            for (Restaurant restaurant : restaurants) {
                try {
                    // Cache basic data
                    restaurantCacheService.cacheRestaurant(restaurant);
                    
                    // Cache for home feed với featured items
                    restaurantCatalogService.cacheRestaurantForHomeFeed(restaurant, Collections.emptyList());
                    
                    cached++;
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to cache restaurant: {} (ID: {})", restaurant.getName(), restaurant.getId());
                }
            }
            
            log.info("🔥 Warmed up restaurant cache: {}/{} restaurants cached", cached, restaurants.size());
            
        } catch (Exception e) {
            log.error("💥 Failed to warm up restaurant cache: {}", e.getMessage());
        }
    }
    
    /**
     * Warm up cache cho tất cả menu items
     */
    public void warmupMenuItemCache() {
        try {
            List<MenuItem> menuItems = menuItemRepository.findAll();
            int cached = 0;
            
            for (MenuItem menuItem : menuItems) {
                try {
                    restaurantCacheService.cacheMenuItem(menuItem);
                    cached++;
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to cache menu item: {} (ID: {})", menuItem.getName(), menuItem.getId());
                }
            }
            
            log.info("🔥 Warmed up menu item cache: {}/{} items cached", cached, menuItems.size());
            
        } catch (Exception e) {
            log.error("💥 Failed to warm up menu item cache: {}", e.getMessage());
        }
    }
    
    /**
     * Warm up cache cho specific restaurant và menu items của nó
     */
    public void warmupRestaurantAndMenuCache(Long restaurantId) {
        try {
            Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
            if (restaurant == null) {
                log.warn("⚠️ Restaurant not found for warmup: {}", restaurantId);
                return;
            }
            
            // Cache restaurant
            restaurantCacheService.cacheRestaurant(restaurant);
            
            // Get menu items for this restaurant
            List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurantId);
            
            // Cache all menu items
            int menuItemsCached = 0;
            for (MenuItem menuItem : menuItems) {
                try {
                    restaurantCacheService.cacheMenuItem(menuItem);
                    menuItemsCached++;
                } catch (Exception e) {
                    log.warn("⚠️ Failed to cache menu item: {}", menuItem.getName());
                }
            }
            
            // Cache restaurant for home feed với featured items (first 3)
            List<Long> featuredItemIds = menuItems.stream()
                .limit(3)
                .map(MenuItem::getId)
                .toList();
            restaurantCatalogService.cacheRestaurantForHomeFeed(restaurant, featuredItemIds);
            
            log.info("🔥 Warmed up cache for restaurant: {} - {} menu items cached", 
                restaurant.getName(), menuItemsCached);
                
        } catch (Exception e) {
            log.error("💥 Failed to warm up cache for restaurant {}: {}", restaurantId, e.getMessage());
        }
    }
    
    /**
     * Clear and refresh all caches
     */
    public void refreshAllCaches() {
        try {
            // Clear catalog
            restaurantCatalogService.refreshRestaurantCatalog();
            
            // Warm up again
            warmupRestaurantCache();
            warmupMenuItemCache();
            
            log.info("🔄 Refreshed all caches successfully");
            
        } catch (Exception e) {
            log.error("💥 Failed to refresh caches: {}", e.getMessage());
        }
    }
}
