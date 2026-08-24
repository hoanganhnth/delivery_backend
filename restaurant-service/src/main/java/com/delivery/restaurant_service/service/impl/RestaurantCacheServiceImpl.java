package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
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
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;

    // Redis Keys - thiết kế để tương thích với future Catalog Service
    private static final String RESTAURANT_KEY_PREFIX = "catalog:restaurant:";
    private static final String MENU_ITEM_KEY_PREFIX = "catalog:menu_item:";

    // TTL cho cache (24 hours)
    private static final long CACHE_TTL_HOURS = 24;

    @Override
    public void cacheRestaurant(Restaurant restaurant) {
        try {
            String key = RESTAURANT_KEY_PREFIX + restaurant.getId();
            redisTemplate.opsForValue().set(
                    key, toRestaurantData(restaurant), CACHE_TTL_HOURS, TimeUnit.HOURS);

            log.info("✅ Cached restaurant: {} -> {}", restaurant.getId(), restaurant.getName());

        } catch (Exception e) {
            log.error("💥 Error caching restaurant {}: {}", restaurant.getId(), e.getMessage());
        }
    }

    @Override
    public void cacheMenuItem(MenuItem menuItem) {
        try {
            String key = MENU_ITEM_KEY_PREFIX + menuItem.getId();
            redisTemplate.opsForValue().set(
                    key, toMenuItemData(menuItem), CACHE_TTL_HOURS, TimeUnit.HOURS);

            log.info("✅ Cached menu item: {} -> {}", menuItem.getId(), menuItem.getName());

        } catch (Exception e) {
            log.error("💥 Error caching menu item {}: {}", menuItem.getId(), e.getMessage());
            throw e;
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

    // ===============================
    // GETTER METHODS FOR VALIDATION
    // ===============================

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRestaurantFromCache(Long restaurantId) {
        try {
            String key = RESTAURANT_KEY_PREFIX + restaurantId;
            Map<String, Object> cachedData = (Map<String, Object>) redisTemplate.opsForValue().get(key);
            if (cachedData != null) {
                return cachedData;
            }
        } catch (Exception e) {
            log.warn("Restaurant cache read failed for {}; falling back to DB: {}",
                    restaurantId, e.getMessage());
        }
        log.info("Cache miss for restaurant {}. Falling back to DB", restaurantId);
        return restaurantRepository.findById(restaurantId)
                .map(restaurant -> {
                    Map<String, Object> data = toRestaurantData(restaurant);
                    cacheRestaurant(restaurant);
                    return data;
                })
                .orElseGet(() -> {
                    log.warn("Restaurant {} not found in DB", restaurantId);
                    return null;
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMenuItemFromCache(Long menuItemId) {
        try {
            String key = MENU_ITEM_KEY_PREFIX + menuItemId;
            Map<String, Object> cachedData = (Map<String, Object>) redisTemplate.opsForValue().get(key);
            if (cachedData != null) {
                return cachedData;
            }
        } catch (Exception e) {
            log.warn("Menu-item cache read failed for {}; falling back to DB: {}",
                    menuItemId, e.getMessage());
        }
        log.info("Cache miss for menu item {}. Falling back to DB", menuItemId);
        return menuItemRepository.findById(menuItemId)
                .map(menuItem -> {
                    Map<String, Object> data = toMenuItemData(menuItem);
                    try {
                        cacheMenuItem(menuItem);
                    } catch (RuntimeException e) {
                        log.warn("Menu-item cache warm failed for {}: {}", menuItemId, e.getMessage());
                    }
                    return data;
                })
                .orElseGet(() -> {
                    log.warn("Menu item {} not found in DB", menuItemId);
                    return null;
                });
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
            Object restaurantIdObj = menuItem.get("restaurantId");
            if (restaurantIdObj == null) {
                log.warn("❌ Menu item {} missing restaurantId field", menuItemId);
                return false;
            }
            
            Long itemRestaurantId = Long.valueOf(restaurantIdObj.toString());
            if (!itemRestaurantId.equals(restaurantId)) {
                log.warn("❌ Menu item {} does not belong to restaurant {}", menuItemId, restaurantId);
                return false;
            }

            // The persisted enum is the canonical availability source. Missing or
            // non-AVAILABLE status must fail closed; there is no UNAVAILABLE enum.
            if (!isAvailableMenuItemStatus(menuItem.get("status"))) {
                log.warn("❌ Menu item {} is not available (status={})",
                        menuItemId, menuItem.get("status"));
                return false;
            }

            // Kiểm tra stock nếu có
            Object stockObj = menuItem.get("stock");
            if (stockObj != null) {
                Integer stock = Integer.valueOf(stockObj.toString());
                if (stock < quantity) {
                    log.warn("❌ Insufficient stock for menu item {}: {} < {}",
                            menuItemId, stock, quantity);
                    return false;
                }
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
            return isWithinOperatingHours(restaurant, LocalTime.now());
        } catch (Exception e) {
            log.error("💥 Error checking operating hours: {}", e.getMessage());
            return false;
        }
    }

    static boolean isWithinOperatingHours(Map<String, Object> restaurant, LocalTime now) {
        Object openingHour = restaurant.get("openingHour");
        Object closingHour = restaurant.get("closingHour");

        if (openingHour != null && closingHour != null) {
            LocalTime open = LocalTime.parse(openingHour.toString(), DateTimeFormatter.ISO_LOCAL_TIME);
            LocalTime close = LocalTime.parse(closingHour.toString(), DateTimeFormatter.ISO_LOCAL_TIME);

            return now.isAfter(open) && now.isBefore(close);
        }

        // Preserve the existing MVP policy for restaurants that do not configure
        // an operating-hours pair. A separate product decision is required before
        // changing those restaurants to fail closed.
        return true;
    }

    static boolean isAvailableMenuItemStatus(Object status) {
        return status != null && MenuItem.Status.AVAILABLE.name().equals(status.toString());
    }

    private Map<String, Object> toRestaurantData(Restaurant restaurant) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", restaurant.getId());
        data.put("name", restaurant.getName());
        data.put("address", restaurant.getAddress());
        data.put("phone", restaurant.getPhone());
        data.put("creatorId", restaurant.getCreatorId());
        data.put("ownerPrincipalId", restaurant.getOwnerPrincipalId());
        data.put("image", restaurant.getImage());
        data.put("openingHour", restaurant.getOpeningHour() != null
                ? restaurant.getOpeningHour().toString() : null);
        data.put("closingHour", restaurant.getClosingHour() != null
                ? restaurant.getClosingHour().toString() : null);
        data.put("defaultPrepTimeMinutes", restaurant.getDefaultPrepTimeMinutes());
        data.put("createdAt", restaurant.getCreatedAt() != null
                ? restaurant.getCreatedAt().toString() : null);
        data.put("updatedAt", restaurant.getUpdatedAt() != null
                ? restaurant.getUpdatedAt().toString() : null);
        data.put("latitude", restaurant.getAddressLat());
        data.put("longitude", restaurant.getAddressLng());
        return data;
    }

    private Map<String, Object> toMenuItemData(MenuItem menuItem) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", menuItem.getId());
        data.put("restaurantId",
                menuItem.getRestaurant() != null ? menuItem.getRestaurant().getId() : null);
        data.put("name", menuItem.getName());
        data.put("description", menuItem.getDescription());
        data.put("price", menuItem.getPrice());
        data.put("status", menuItem.getStatus());
        data.put("image", menuItem.getImage());
        data.put("createdAt", menuItem.getCreatedAt() != null
                ? menuItem.getCreatedAt().toString() : null);
        data.put("updatedAt", menuItem.getUpdatedAt() != null
                ? menuItem.getUpdatedAt().toString() : null);
        return data;
    }
}
