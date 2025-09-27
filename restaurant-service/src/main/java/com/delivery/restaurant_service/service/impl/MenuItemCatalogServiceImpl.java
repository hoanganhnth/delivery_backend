// package com.delivery.restaurant_service.service.impl;

// import com.delivery.restaurant_service.dto.response.MenuItemCatalogResponse;
// import com.delivery.restaurant_service.entity.MenuItem;
// import com.delivery.restaurant_service.service.MenuItemCatalogService;
// import com.fasterxml.jackson.core.JsonProcessingException;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.data.redis.core.RedisTemplate;
// import org.springframework.data.redis.core.ZSetOperations;
// import org.springframework.stereotype.Service;

// import java.time.Duration;
// import java.util.*;
// import java.util.stream.Collectors;

// /**
//  * MenuItemCatalog Service Implementation
//  * Tối ưu Redis cho Restaurant Detail Page - Full menu data
//  */
// @Slf4j
// @Service
// public class MenuItemCatalogServiceImpl implements MenuItemCatalogService {
    
//     private final RedisTemplate<String, Object> redisTemplate;
//     private final ObjectMapper objectMapper;
    
//     // Redis Key Patterns - Tối ưu cho menu queries
//     private static final String MENU_ITEM_KEY = "menu_catalog:item:%d"; // Single menu item
//     private static final String RESTAURANT_MENU_KEY = "menu_catalog:restaurant:%d"; // Full menu as Hash
//     private static final String CATEGORY_KEY = "menu_catalog:restaurant:%d:category:%s"; // Menu by category
//     private static final String FEATURED_KEY = "menu_catalog:restaurant:%d:featured"; // Featured items (ZSet)
//     private static final String CATEGORIES_KEY = "menu_catalog:restaurant:%d:categories"; // Restaurant categories (Set)
    
//     private static final Duration CACHE_TTL = Duration.ofHours(24); // 24 hours cache
//     private static final Duration FEATURED_TTL = Duration.ofHours(6); // Featured items refresh more frequently
    
//     public MenuItemCatalogServiceImpl(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
//         this.redisTemplate = redisTemplate;
//         this.objectMapper = objectMapper;
//     }
    
//     @Override
//     public void cacheMenuItem(MenuItem menuItem) {
//         try {
//             MenuItemCatalogResponse response = convertToMenuItemResponse(menuItem);
//             String key = String.format(MENU_ITEM_KEY, menuItem.getId());
            
//             // Cache as JSON string
//             redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), CACHE_TTL);
            
//             log.info("📦 Cached menu item: {} for restaurant: {}", menuItem.getName(), menuItem.getRestaurant().getId());
            
//         } catch (JsonProcessingException e) {
//             log.error("❌ Failed to cache menu item: {}", menuItem.getName(), e);
//         }
//     }
    
//     @Override
//     public void cacheRestaurantMenu(Long restaurantId, List<MenuItem> menuItems) {
//         try {
//             String menuKey = String.format(RESTAURANT_MENU_KEY, restaurantId);
//             String featuredKey = String.format(FEATURED_KEY, restaurantId);
            
//             // 1. Cache toàn bộ menu as Hash (menuItemId -> JSON)
//             Map<String, String> menuHash = new HashMap<>();
//             List<ZSetOperations.TypedTuple<Object>> featuredItems = new ArrayList<>();
            
//             for (MenuItem item : menuItems) {
//                 MenuItemCatalogResponse response = convertToMenuItemResponse(item);
//                 String itemJson = objectMapper.writeValueAsString(response);
                
//                 // Add to main menu hash
//                 menuHash.put(item.getId().toString(), itemJson);
                
//                 // Add to featured if popular or available
//                 if (Boolean.TRUE.equals(response.getIsPopular()) || Boolean.TRUE.equals(response.getIsRecommended())) {
//                     double score = response.getOrderCount() != null ? response.getOrderCount() : 0;
//                     featuredItems.add(ZSetOperations.TypedTuple.of(item.getId().toString(), score));
//                 }
                
//                 // Cache individual item
//                 cacheMenuItem(item);
//             }
            
//             // Store menu hash
//             redisTemplate.opsForHash().putAll(menuKey, menuHash);
//             redisTemplate.expire(menuKey, CACHE_TTL);
            
//             // Store featured items (sorted by popularity)
//             if (!featuredItems.isEmpty()) {
//                 redisTemplate.opsForZSet().add(featuredKey, new HashSet<>(featuredItems));
//                 redisTemplate.expire(featuredKey, FEATURED_TTL);
//             }
            
//             log.info("🍽️ Cached complete menu for restaurant: {} ({} items)", 
//                     restaurantId, menuItems.size());
            
//         } catch (JsonProcessingException e) {
//             log.error("❌ Failed to cache restaurant menu: {}", restaurantId, e);
//         }
//     }
    
//     @Override
//     public List<MenuItemCatalogResponse> getRestaurantMenu(Long restaurantId) {
//         try {
//             String key = String.format(RESTAURANT_MENU_KEY, restaurantId);
//             Map<Object, Object> menuHash = redisTemplate.opsForHash().entries(key);
            
//             if (menuHash.isEmpty()) {
//                 log.info("🔍 Menu not found in cache for restaurant: {}", restaurantId);
//                 return Collections.emptyList();
//             }
            
//             List<MenuItemCatalogResponse> items = new ArrayList<>();
//             for (Object jsonString : menuHash.values()) {
//                 try {
//                     MenuItemCatalogResponse item = objectMapper.readValue((String) jsonString, MenuItemCatalogResponse.class);
//                     items.add(item);
//                 } catch (JsonProcessingException e) {
//                     log.warn("⚠️ Failed to deserialize menu item from cache", e);
//                 }
//             }
            
//             // Sort by category then by name
//             items.sort(Comparator.comparing(MenuItemCatalogResponse::getCategory, Comparator.nullsLast(Comparator.naturalOrder()))
//                       .thenComparing(MenuItemCatalogResponse::getName));
            
//             log.info("📋 Retrieved {} menu items for restaurant: {}", items.size(), restaurantId);
//             return items;
            
//         } catch (Exception e) {
//             log.error("❌ Failed to get restaurant menu: {}", restaurantId, e);
//             return Collections.emptyList();
//         }
//     }
    
//     @Override
//     public List<MenuItemCatalogResponse> getMenuItemsByCategory(Long restaurantId, String category) {
//         try {
//             String key = String.format(CATEGORY_KEY, restaurantId, category);
//             List<Object> categoryItems = redisTemplate.opsForList().range(key, 0, -1);
            
//             if (categoryItems == null || categoryItems.isEmpty()) {
//                 log.info("🔍 Category '{}' not found in cache for restaurant: {}", category, restaurantId);
//                 return Collections.emptyList();
//             }
            
//             List<MenuItemCatalogResponse> items = new ArrayList<>();
//             for (Object jsonString : categoryItems) {
//                 try {
//                     MenuItemCatalogResponse item = objectMapper.readValue((String) jsonString, MenuItemCatalogResponse.class);
//                     items.add(item);
//                 } catch (JsonProcessingException e) {
//                     log.warn("⚠️ Failed to deserialize category item from cache", e);
//                 }
//             }
            
//             log.info("📂 Retrieved {} items for category '{}' in restaurant: {}", items.size(), category, restaurantId);
//             return items;
            
//         } catch (Exception e) {
//             log.error("❌ Failed to get menu items by category: {} for restaurant: {}", category, restaurantId, e);
//             return Collections.emptyList();
//         }
//     }
    
//     @Override
//     public List<MenuItemCatalogResponse> getFeaturedMenuItems(Long restaurantId, int limit) {
//         try {
//             String key = String.format(FEATURED_KEY, restaurantId);
            
//             // Get top featured items (highest scores first)
//             Set<Object> featuredIds = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
            
//             if (featuredIds == null || featuredIds.isEmpty()) {
//                 log.info("🔍 No featured items found for restaurant: {}", restaurantId);
//                 return Collections.emptyList();
//             }
            
//             List<MenuItemCatalogResponse> items = new ArrayList<>();
//             for (Object itemId : featuredIds) {
//                 MenuItemCatalogResponse item = getMenuItemDetail(Long.valueOf(itemId.toString()));
//                 if (item != null) {
//                     items.add(item);
//                 }
//             }
            
//             log.info("⭐ Retrieved {} featured items for restaurant: {}", items.size(), restaurantId);
//             return items;
            
//         } catch (Exception e) {
//             log.error("❌ Failed to get featured menu items for restaurant: {}", restaurantId, e);
//             return Collections.emptyList();
//         }
//     }
    
//     @Override
//     public List<MenuItemCatalogResponse> searchMenuItems(Long restaurantId, String keyword) {
//         // Simple implementation - get all items and filter by keyword
//         // For advanced search, consider using Redis Search module
//         List<MenuItemCatalogResponse> allItems = getRestaurantMenu(restaurantId);
//         String searchTerm = keyword.toLowerCase();
        
//         return allItems.stream()
//                 .filter(item -> item.getName().toLowerCase().contains(searchTerm) ||
//                                (item.getDescription() != null && item.getDescription().toLowerCase().contains(searchTerm)) ||
//                                (item.getCategory() != null && item.getCategory().toLowerCase().contains(searchTerm)))
//                 .collect(Collectors.toList());
//     }
    
//     @Override
//     public MenuItemCatalogResponse getMenuItemDetail(Long menuItemId) {
//         try {
//             String key = String.format(MENU_ITEM_KEY, menuItemId);
//             Object itemJson = redisTemplate.opsForValue().get(key);
            
//             if (itemJson == null) {
//                 log.info("🔍 Menu item not found in cache: {}", menuItemId);
//                 return null;
//             }
            
//             MenuItemCatalogResponse item = objectMapper.readValue((String) itemJson, MenuItemCatalogResponse.class);
//             log.info("🍽️ Retrieved menu item detail: {}", item.getName());
//             return item;
            
//         } catch (JsonProcessingException e) {
//             log.error("❌ Failed to get menu item detail: {}", menuItemId, e);
//             return null;
//         }
//     }
    
//     @Override
//     public void updateMenuItemAvailability(Long menuItemId, boolean isAvailable) {
//         try {
//             String key = String.format(MENU_ITEM_KEY, menuItemId);
//             Object itemJson = redisTemplate.opsForValue().get(key);
            
//             if (itemJson != null) {
//                 MenuItemCatalogResponse item = objectMapper.readValue((String) itemJson, MenuItemCatalogResponse.class);
//                 item.setIsAvailable(isAvailable);
                
//                 // Update in cache
//                 redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(item), CACHE_TTL);
                
//                 // Update in restaurant menu hash
//                 String menuKey = String.format(RESTAURANT_MENU_KEY, item.getRestaurantId());
//                 redisTemplate.opsForHash().put(menuKey, menuItemId.toString(), objectMapper.writeValueAsString(item));
                
//                 log.info("✅ Updated availability for menu item: {} -> {}", item.getName(), isAvailable);
//             }
            
//         } catch (JsonProcessingException e) {
//             log.error("❌ Failed to update menu item availability: {}", menuItemId, e);
//         }
//     }
    
//     @Override
//     public void removeMenuItemFromCatalog(Long menuItemId) {
//         try {
//             // Get item detail first to know restaurant ID
//             MenuItemCatalogResponse item = getMenuItemDetail(menuItemId);
//             if (item == null) {
//                 log.warn("⚠️ Menu item not found for removal: {}", menuItemId);
//                 return;
//             }
            
//             Long restaurantId = item.getRestaurantId();
            
//             // Remove from individual cache
//             String itemKey = String.format(MENU_ITEM_KEY, menuItemId);
//             redisTemplate.delete(itemKey);
            
//             // Remove from restaurant menu hash
//             String menuKey = String.format(RESTAURANT_MENU_KEY, restaurantId);
//             redisTemplate.opsForHash().delete(menuKey, menuItemId.toString());
            
//             // Remove from featured if exists
//             String featuredKey = String.format(FEATURED_KEY, restaurantId);
//             redisTemplate.opsForZSet().remove(featuredKey, menuItemId.toString());
            
//             log.info("🗑️ Removed menu item from catalog: {}", item.getName());
            
//         } catch (Exception e) {
//             log.error("❌ Failed to remove menu item from catalog: {}", menuItemId, e);
//         }
//     }
    
//     @Override
//     public List<String> getRestaurantCategories(Long restaurantId) {
//         try {
//             String key = String.format(CATEGORIES_KEY, restaurantId);
//             Set<Object> categories = redisTemplate.opsForSet().members(key);
            
//             if (categories == null || categories.isEmpty()) {
//                 return Collections.emptyList();
//             }
            
//             List<String> categoryList = categories.stream()
//                     .map(Object::toString)
//                     .sorted()
//                     .collect(Collectors.toList());
            
//             log.info("📂 Retrieved {} categories for restaurant: {}", categoryList.size(), restaurantId);
//             return categoryList;
            
//         } catch (Exception e) {
//             log.error("❌ Failed to get restaurant categories: {}", restaurantId, e);
//             return Collections.emptyList();
//         }
//     }
    
//     @Override
//     public void refreshRestaurantMenuCatalog(Long restaurantId) {
//         // Clear all related keys
//         String menuKey = String.format(RESTAURANT_MENU_KEY, restaurantId);
//         String categoriesKey = String.format(CATEGORIES_KEY, restaurantId);
//         String featuredKey = String.format(FEATURED_KEY, restaurantId);
        
//         redisTemplate.delete(menuKey);
//         redisTemplate.delete(categoriesKey);
//         redisTemplate.delete(featuredKey);
        
//         // Clear category-specific keys
//         List<String> categories = getRestaurantCategories(restaurantId);
//         for (String category : categories) {
//             String categoryKey = String.format(CATEGORY_KEY, restaurantId, category);
//             redisTemplate.delete(categoryKey);
//         }
        
//         log.info("🔄 Refreshed menu catalog for restaurant: {}", restaurantId);
//     }
    
//     // Helper Methods
    
//     private MenuItemCatalogResponse convertToMenuItemResponse(MenuItem menuItem) {
//         MenuItemCatalogResponse response = new MenuItemCatalogResponse();
        
//         // Core fields
//         response.setMenuItemId(menuItem.getId());
//         response.setName(menuItem.getName());
//         response.setDescription(menuItem.getDescription());
//         response.setPrice(menuItem.getPrice());
//         response.setCategory("General"); // Default category since MenuItem doesn't have category
//         response.setIsAvailable(menuItem.getStatus() == MenuItem.Status.AVAILABLE);
//         response.setImageUrl(menuItem.getImage());
        
//         // Restaurant info
//         if (menuItem.getRestaurant() != null) {
//             response.setRestaurantId(menuItem.getRestaurant().getId());
//             response.setRestaurantName(menuItem.getRestaurant().getName());
//         }
        
//         // Default values for missing fields - sẽ được update từ database sau
//         response.setIsPopular(false);
//         response.setIsRecommended(false);
//         response.setOrderCount(0);
//         response.setRating(0.0);
//         response.setReviewCount(0);
//         response.setLastUpdated(java.time.LocalDateTime.now());
        
//         return response;
//     }
// }
