package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation cache service cho Restaurant và Menu data
 * Thiết kế key structure tương thích với future Catalog Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantCacheServiceImpl implements RestaurantCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis Keys - thiết kế để tương thích với future Catalog Service
    private static final String RESTAURANT_KEY_PREFIX = "catalog:restaurant:";
    private static final String MENU_ITEM_KEY_PREFIX = "catalog:menu_item:";
    
    // TTL cho cache (24 hours)
    private static final long CACHE_TTL_HOURS = 24;
    
    @Override
    public void cacheRestaurant(Restaurant restaurant) {
        try {
            String key = RESTAURANT_KEY_PREFIX + restaurant.getId();
            
            Map<String, Object> restaurantData = new HashMap<>();
            restaurantData.put("id", restaurant.getId());
            restaurantData.put("name", restaurant.getName());
            restaurantData.put("address", restaurant.getAddress());
            restaurantData.put("phone", restaurant.getPhone());
            restaurantData.put("creatorId", restaurant.getCreatorId());
            restaurantData.put("openingHour", restaurant.getOpeningHour());
            restaurantData.put("closingHour", restaurant.getClosingHour());
            restaurantData.put("image", restaurant.getImage());
            restaurantData.put("createdAt", restaurant.getCreatedAt());
            restaurantData.put("updatedAt", restaurant.getUpdatedAt());
            
            redisTemplate.opsForValue().set(key, restaurantData, CACHE_TTL_HOURS, TimeUnit.HOURS);
            
            log.info("✅ Cached restaurant: {} -> {}", restaurant.getId(), restaurant.getName());
            
        } catch (Exception e) {
            log.error("💥 Error caching restaurant {}: {}", restaurant.getId(), e.getMessage());
        }
    }
    
    @Override
    public void cacheMenuItem(MenuItem menuItem) {
        try {
            String key = MENU_ITEM_KEY_PREFIX + menuItem.getId();
            
            Map<String, Object> menuItemData = new HashMap<>();
            menuItemData.put("id", menuItem.getId());
            menuItemData.put("restaurant", menuItem.getRestaurant() != null ? menuItem.getRestaurant().getId() : null);
            menuItemData.put("name", menuItem.getName());
            menuItemData.put("description", menuItem.getDescription());
            menuItemData.put("price", menuItem.getPrice());
            menuItemData.put("status", menuItem.getStatus());
            menuItemData.put("image", menuItem.getImage());
            menuItemData.put("createdAt", menuItem.getCreatedAt());
            menuItemData.put("updatedAt", menuItem.getUpdatedAt());
            
            redisTemplate.opsForValue().set(key, menuItemData, CACHE_TTL_HOURS, TimeUnit.HOURS);
            
            log.info("✅ Cached menu item: {} -> {}", menuItem.getId(), menuItem.getName());
            
        } catch (Exception e) {
            log.error("💥 Error caching menu item {}: {}", menuItem.getId(), e.getMessage());
        }
    }
    
    @Override
    public void removeRestaurantFromCache(Long restaurantId) {
        try {
            String key = RESTAURANT_KEY_PREFIX + restaurantId;
            redisTemplate.delete(key);
            
            log.info("🗑️ Removed restaurant from cache: {}", restaurantId);
            
        } catch (Exception e) {
            log.error("💥 Error removing restaurant {} from cache: {}", restaurantId, e.getMessage());
        }
    }
    
    @Override
    public void removeMenuItemFromCache(Long menuItemId) {
        try {
            String key = MENU_ITEM_KEY_PREFIX + menuItemId;
            redisTemplate.delete(key);
            
            log.info("🗑️ Removed menu item from cache: {}", menuItemId);
            
        } catch (Exception e) {
            log.error("💥 Error removing menu item {} from cache: {}", menuItemId, e.getMessage());
        }
    }
    
    @Override
    public void updateRestaurantAvailability(Long restaurantId, boolean isAvailable) {
        try {
            String key = RESTAURANT_KEY_PREFIX + restaurantId;
            Map<String, Object> restaurant = (Map<String, Object>) redisTemplate.opsForValue().get(key);
            
            if (restaurant != null) {
                // Since Restaurant entity doesn't have isAvailable field, 
                // we'll add a custom field for cache purposes
                restaurant.put("cacheIsAvailable", isAvailable);
                redisTemplate.opsForValue().set(key, restaurant, CACHE_TTL_HOURS, TimeUnit.HOURS);
                
                log.info("🔄 Updated restaurant {} availability: {}", restaurantId, isAvailable);
            }
            
        } catch (Exception e) {
            log.error("💥 Error updating restaurant {} availability: {}", restaurantId, e.getMessage());
        }
    }
    
    @Override
    public void updateMenuItemAvailability(Long menuItemId, boolean isAvailable) {
        try {
            String key = MENU_ITEM_KEY_PREFIX + menuItemId;
            Map<String, Object> menuItem = (Map<String, Object>) redisTemplate.opsForValue().get(key);
            
            if (menuItem != null) {
                // MenuItem has status field, so we'll update based on availability
                MenuItem.Status newStatus = isAvailable ? MenuItem.Status.AVAILABLE : MenuItem.Status.SOLD_OUT;
                menuItem.put("status", newStatus);
                redisTemplate.opsForValue().set(key, menuItem, CACHE_TTL_HOURS, TimeUnit.HOURS);
                
                log.info("🔄 Updated menu item {} availability: {} (status: {})", menuItemId, isAvailable, newStatus);
            }
            
        } catch (Exception e) {
            log.error("💥 Error updating menu item {} availability: {}", menuItemId, e.getMessage());
        }
    }
    
    // ===============================
    // GETTER METHODS FOR VALIDATION
    // ===============================
    
    @Override
    public Map<String, Object> getRestaurantFromCache(Long restaurantId) {
        try {
            String key = RESTAURANT_KEY_PREFIX + restaurantId;
            return (Map<String, Object>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("💥 Error getting restaurant {} from cache: {}", restaurantId, e.getMessage());
            return null;
        }
    }
    
    @Override
    public Map<String, Object> getMenuItemFromCache(Long menuItemId) {
        try {
            String key = MENU_ITEM_KEY_PREFIX + menuItemId;
            return (Map<String, Object>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("💥 Error getting menu item {} from cache: {}", menuItemId, e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean isRestaurantInCache(Long restaurantId) {
        try {
            String key = RESTAURANT_KEY_PREFIX + restaurantId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("💥 Error checking restaurant {} existence: {}", restaurantId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isMenuItemInCache(Long menuItemId) {
        try {
            String key = MENU_ITEM_KEY_PREFIX + menuItemId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("💥 Error checking menu item {} existence: {}", menuItemId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public Double getMenuItemPrice(Long menuItemId) {
        try {
            Map<String, Object> menuItem = getMenuItemFromCache(menuItemId);
            if (menuItem != null && menuItem.get("price") != null) {
                return Double.valueOf(menuItem.get("price").toString());
            }
            return null;
        } catch (Exception e) {
            log.error("💥 Error getting menu item {} price: {}", menuItemId, e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean isRestaurantAvailable(Long restaurantId) {
        try {
            Map<String, Object> restaurant = getRestaurantFromCache(restaurantId);
            if (restaurant == null) {
                return false;
            }
            
            // Kiểm tra isAvailable
            Boolean isAvailable = (Boolean) restaurant.get("isAvailable");
            if (Boolean.FALSE.equals(isAvailable)) {
                return false;
            }
            
            // Kiểm tra isOpen
            Boolean isOpen = (Boolean) restaurant.get("isOpen");
            if (Boolean.FALSE.equals(isOpen)) {
                return false;
            }
            
            // Kiểm tra operating hours
            return checkOperatingHours(restaurant);
            
        } catch (Exception e) {
            log.error("💥 Error checking restaurant {} availability: {}", restaurantId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isMenuItemAvailable(Long restaurantId, Long menuItemId, Integer quantity) {
        try {
            Map<String, Object> menuItem = getMenuItemFromCache(menuItemId);
            if (menuItem == null) {
                log.warn("❌ Menu item not found in cache: {}", menuItemId);
                return false;
            }
            
            // Kiểm tra menu item thuộc restaurant này không
            Long itemRestaurantId = Long.valueOf(menuItem.get("restaurantId").toString());
            if (!itemRestaurantId.equals(restaurantId)) {
                log.warn("❌ Menu item {} does not belong to restaurant {}", menuItemId, restaurantId);
                return false;
            }
            
            // Kiểm tra available
            Boolean isAvailable = (Boolean) menuItem.get("isAvailable");
            if (Boolean.FALSE.equals(isAvailable)) {
                log.warn("❌ Menu item {} is not available", menuItemId);
                return false;
            }
            
            // Kiểm tra stock nếu có
            Integer stock = menuItem.get("stock") != null ? 
                    Integer.valueOf(menuItem.get("stock").toString()) : null;
            if (stock != null && stock < quantity) {
                log.warn("❌ Insufficient stock for menu item {}: {} < {}", 
                        menuItemId, stock, quantity);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("💥 Error validating menu item {}: {}", menuItemId, e.getMessage());
            return false;
        }
    }
    
    // Private helper method để check operating hours
    private boolean checkOperatingHours(Map<String, Object> restaurant) {
        try {
            String openTime = (String) restaurant.get("openTime");
            String closeTime = (String) restaurant.get("closeTime");
            
            if (openTime != null && closeTime != null) {
                LocalTime now = LocalTime.now();
                LocalTime open = LocalTime.parse(openTime, DateTimeFormatter.ofPattern("HH:mm"));
                LocalTime close = LocalTime.parse(closeTime, DateTimeFormatter.ofPattern("HH:mm"));
                
                return now.isAfter(open) && now.isBefore(close);
            }
            
            return true; // Nếu không có thời gian hoạt động thì coi như luôn mở
            
        } catch (Exception e) {
            log.error("💥 Error checking operating hours: {}", e.getMessage());
            return false;
        }
    }
}
