package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.impl.MenuItemServiceImpl;
import com.delivery.restaurant_service.service.impl.RestaurantServiceImpl;
import com.delivery.restaurant_service.service.impl.CacheWarmupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for cache management and availability updates
 */
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Slf4j
public class CacheController {
    
    private final RestaurantServiceImpl restaurantService;
    private final MenuItemServiceImpl menuItemService;
    private final CacheWarmupService cacheWarmupService;
    
    /**
     * Update restaurant availability
     */
    @PutMapping("/restaurants/{restaurantId}/availability")
    public ResponseEntity<BaseResponse<String>> updateRestaurantAvailability(
            @PathVariable Long restaurantId,
            @RequestParam boolean isAvailable,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        
        restaurantService.updateRestaurantAvailability(restaurantId, isAvailable, userId);
        
        String message = String.format("Restaurant %d availability updated to: %s", restaurantId, isAvailable);
        return ResponseEntity.ok(new BaseResponse<>(1, message));
    }
    
    /**
     * Update menu item availability
     */
    @PutMapping("/menu-items/{menuItemId}/availability")
    public ResponseEntity<BaseResponse<String>> updateMenuItemAvailability(
            @PathVariable Long menuItemId,
            @RequestParam boolean isAvailable,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        
        menuItemService.updateMenuItemAvailability(menuItemId, isAvailable, userId);
        
        String message = String.format("Menu item %d availability updated to: %s", menuItemId, isAvailable);
        return ResponseEntity.ok(new BaseResponse<>(1, message));
    }
    
    /**
     * Warm up restaurant cache
     */
    @PostMapping("/warmup/restaurants")
    public ResponseEntity<BaseResponse<String>> warmupRestaurantCache() {
        cacheWarmupService.warmupRestaurantCache();
        return ResponseEntity.ok(new BaseResponse<>(1, "Restaurant cache warmed up successfully"));
    }
    
    /**
     * Warm up menu item cache  
     */
    @PostMapping("/warmup/menu-items")
    public ResponseEntity<BaseResponse<String>> warmupMenuItemCache() {
        cacheWarmupService.warmupMenuItemCache();
        return ResponseEntity.ok(new BaseResponse<>(1, "Menu item cache warmed up successfully"));
    }
    
    /**
     * Warm up cache for specific restaurant
     */
    @PostMapping("/warmup/restaurants/{restaurantId}")
    public ResponseEntity<BaseResponse<String>> warmupRestaurantCache(@PathVariable Long restaurantId) {
        cacheWarmupService.warmupRestaurantAndMenuCache(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, "Restaurant cache warmed up for ID: " + restaurantId));
    }
    
    /**
     * Refresh all caches
     */
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<String>> refreshAllCaches() {
        cacheWarmupService.refreshAllCaches();
        return ResponseEntity.ok(new BaseResponse<>(1, "All caches refreshed successfully"));
    }
}
