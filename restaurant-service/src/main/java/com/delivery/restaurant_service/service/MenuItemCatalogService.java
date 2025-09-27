package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.response.MenuItemCatalogResponse;
import com.delivery.restaurant_service.entity.MenuItem;

import java.util.List;

/**
 * Service cho MenuItem Catalog - tối ưu cho Restaurant Detail Page
 * Cache full menu data theo categories
 */
public interface MenuItemCatalogService {
    
    /**
     * Cache menu item detail
     */
    void cacheMenuItem(MenuItem menuItem);
    
    /**
     * Cache toàn bộ menu của restaurant theo categories
     */
    void cacheRestaurantMenu(Long restaurantId, List<MenuItem> menuItems);
    
    /**
     * Get toàn bộ menu của restaurant (có group by category)
     */
    List<MenuItemCatalogResponse> getRestaurantMenu(Long restaurantId);
    
    /**
     * Get menu items by category
     */
    List<MenuItemCatalogResponse> getMenuItemsByCategory(Long restaurantId, String category);
    
    /**
     * Get featured/popular menu items của restaurant
     */
    List<MenuItemCatalogResponse> getFeaturedMenuItems(Long restaurantId, int limit);
    
    /**
     * Search menu items trong restaurant
     */
    List<MenuItemCatalogResponse> searchMenuItems(Long restaurantId, String keyword);
    
    /**
     * Get single menu item detail
     */
    MenuItemCatalogResponse getMenuItemDetail(Long menuItemId);
    
    /**
     * Update menu item availability
     */
    void updateMenuItemAvailability(Long menuItemId, boolean isAvailable);
    
    /**
     * Remove menu item khỏi catalog
     */
    void removeMenuItemFromCatalog(Long menuItemId);
    
    /**
     * Get all categories của restaurant
     */
    List<String> getRestaurantCategories(Long restaurantId);
    
    /**
     * Refresh toàn bộ menu catalog của restaurant
     */
    void refreshRestaurantMenuCatalog(Long restaurantId);
}
