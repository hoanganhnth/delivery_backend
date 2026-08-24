package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.service.OrderCacheValidationService;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import com.delivery.restaurant_service.service.RestaurantServiceabilityService;
import com.delivery.restaurant_service.service.ServiceabilityDecision;
import com.delivery.restaurant_service.service.MenuItemInventoryReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation validation service cho order format từ order-service
 * Sử dụng RestaurantCacheService để lấy data từ Redis
 */
@Service
@Slf4j
public class OrderCacheValidationServiceImpl implements OrderCacheValidationService {

    private final RestaurantCacheService restaurantCacheService;
    private final RestaurantServiceabilityService serviceabilityService;
    private final ObjectProvider<MenuItemInventoryReservationService> inventoryServiceProvider;

    @Autowired
    public OrderCacheValidationServiceImpl(RestaurantCacheService restaurantCacheService,
                                           RestaurantServiceabilityService serviceabilityService,
                                           ObjectProvider<MenuItemInventoryReservationService> inventoryServiceProvider) {
        this.restaurantCacheService = restaurantCacheService;
        this.serviceabilityService = serviceabilityService;
        this.inventoryServiceProvider = inventoryServiceProvider;
    }

    /** Compatibility constructor retained for focused serviceability callers. */
    public OrderCacheValidationServiceImpl(RestaurantCacheService restaurantCacheService,
                                           RestaurantServiceabilityService serviceabilityService) {
        this(restaurantCacheService, serviceabilityService, null);
    }

    /** Compatibility constructor for cache-only unit tests and old callers. */
    public OrderCacheValidationServiceImpl(RestaurantCacheService restaurantCacheService) {
        this(restaurantCacheService, null, null);
    }
    
    @Override
    public OrderValidationResultResponse validateOrderFromOrderService(OrderValidationRequest request) {
        log.info("🔍 Validating order from order-service for restaurant: {}", request.getRestaurantId());
        
        List<OrderValidationResultResponse.ValidationError> errors = new ArrayList<>();
        List<OrderValidationResultResponse.ItemValidationInfo> itemValidations = new ArrayList<>();
        
        // 1. Validate restaurant info
        OrderValidationResultResponse.RestaurantInfo restaurantInfo = 
                validateRestaurantInfo(request.getRestaurantId(), errors);

        ServiceabilityDecision serviceability = serviceabilityService == null
                ? ServiceabilityDecision.disabled()
                : serviceabilityService.evaluate(request.getRestaurantId(),
                        request.getDeliveryLat(), request.getDeliveryLng());
        if (restaurantInfo != null) {
            restaurantInfo.setServiceabilityEnabled(serviceability.enabled());
            restaurantInfo.setServiceable(serviceability.enabled() ? serviceability.serviceable() : null);
            restaurantInfo.setServiceabilityZoneId(serviceability.zoneId());
            restaurantInfo.setServiceabilityZoneRevision(serviceability.zoneRevision());
            restaurantInfo.setServiceabilityReason(serviceability.reason());
        }
        if (serviceability.enabled() && !serviceability.serviceable()) {
            errors.add(OrderValidationResultResponse.ValidationError.builder()
                    .field("deliveryCoordinate")
                    .errorCode(serviceability.reason())
                    .message("Địa chỉ giao hàng nằm ngoài vùng phục vụ")
                    .invalidValue(request.getDeliveryLat() + "," + request.getDeliveryLng())
                    .build());
        }
        
        // 2. Validate từng món và tính tổng tiền
        Double calculatedTotal = 0.0;
        for (OrderValidationRequest.OrderItemRequest item : request.getItems()) {
            OrderValidationResultResponse.ItemValidationInfo itemValidation = 
                    validateMenuItem(request.getRestaurantId(), item);
            itemValidations.add(itemValidation);
            
            // Thêm vào errors nếu có vấn đề
            if (!itemValidation.getIsAvailable()) {
                errors.add(OrderValidationResultResponse.ValidationError.builder()
                        .field("menuItem")
                        .errorCode("MENU_ITEM_NOT_AVAILABLE")
                        .message("Món ăn " + itemValidation.getMenuItemName() + " không khả dụng")
                        .invalidValue(item.getMenuItemId())
                        .build());
            }
            
            if (!itemValidation.getHasEnoughStock()) {
                // Create map with null-safe values for stock info
                Map<String, Object> stockInfo = new HashMap<>();
                stockInfo.put("requested", itemValidation.getRequestedQuantity());
                stockInfo.put("available", itemValidation.getAvailableStock());
                
                errors.add(OrderValidationResultResponse.ValidationError.builder()
                        .field("stock")
                        .errorCode("INSUFFICIENT_STOCK")
                        .message("Không đủ hàng cho món " + itemValidation.getMenuItemName())
                        .invalidValue(stockInfo)
                        .build());
            }
            
            // Cộng vào tổng tiền (dùng giá thực tế từ cache)
            if (itemValidation.getActualPrice() != null) {
                calculatedTotal += itemValidation.getActualPrice() * item.getQuantity();
            }
        }
        
        boolean isValid = errors.isEmpty() && restaurantInfo != null && restaurantInfo.getIsAvailable()
                && (!serviceability.enabled() || serviceability.serviceable());
        String message = isValid ? "Order validation successful" : "Order validation failed";
        
        return OrderValidationResultResponse.builder()
                .isValid(isValid)
                .message(message)
                .calculatedTotal(calculatedTotal)
                .errors(errors)
                .restaurantInfo(restaurantInfo)
                .itemValidations(itemValidations)
                .build();
    }
    
    private OrderValidationResultResponse.ItemValidationInfo validateMenuItem(
            Long restaurantId, OrderValidationRequest.OrderItemRequest item) {
        
        // Lấy thông tin từ cache với null check
        Map<String, Object> menuItemData = restaurantCacheService.getMenuItemFromCache(item.getMenuItemId());
        
        // Kiểm tra menuItemData có tồn tại không
        if (menuItemData == null) {
            log.warn("⚠️ Menu item {} not found in cache", item.getMenuItemId());
            return OrderValidationResultResponse.ItemValidationInfo.builder()
                    .menuItemId(item.getMenuItemId())
                    .menuItemName(null)
                    .isAvailable(false)
                    .actualPrice(null)
                    .expectedPrice(null)
                    .priceMatches(false)
                    .requestedQuantity(item.getQuantity())
                    .availableStock(0)
                    .hasEnoughStock(false)
                    .build();
        }
        
        // Null-safe extraction của actual price từ cache data
        Double actualPrice = null;
        Object priceObj = menuItemData.get("price");
        if (priceObj != null) {
            try {
                actualPrice = Double.valueOf(priceObj.toString());
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid price format for menu item {}: {}", item.getMenuItemId(), priceObj);
            }
        }
        
        // Null-safe validation cho restaurant ownership
        boolean belongsToRestaurant = false;
        Object restaurantIdObj = menuItemData.get("restaurantId");
        if (restaurantIdObj != null) {
            try {
                Long itemRestaurantId = Long.valueOf(restaurantIdObj.toString());
                belongsToRestaurant = itemRestaurantId.equals(restaurantId);
                if (!belongsToRestaurant) {
                    log.warn("⚠️ Menu item {} does not belong to restaurant {}", item.getMenuItemId(), restaurantId);
                }
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid restaurantId format for menu item {}: {}", item.getMenuItemId(), restaurantIdObj);
                belongsToRestaurant = false;
            }
        }
        
        // The canonical entity enum has AVAILABLE/SOLD_OUT/DISCONTINUED.
        // Missing or any non-AVAILABLE value fails closed.
        Object statusObj = menuItemData.get("status");
        boolean statusAvailable = statusObj != null
                && MenuItem.Status.AVAILABLE.name().equals(statusObj.toString());
        
        Object nameObj = menuItemData.get("name");
        String canonicalName = nameObj != null ? nameObj.toString() : null;
        boolean hasCanonicalPrice = actualPrice != null
                && Double.isFinite(actualPrice) && actualPrice > 0;

        // Overall availability check. Missing canonical identity data fails closed.
        boolean isAvailable = belongsToRestaurant && statusAvailable
                             && canonicalName != null && !canonicalName.isBlank()
                             && hasCanonicalPrice
                             && restaurantCacheService.isRestaurantAvailable(restaurantId);
        
        // When inventory is off, preserve the old catalog-only compatibility
        // behavior. When it is enabled, Restaurant's durable inventory ledger
        // is the authority; a missing row fails closed instead of assuming
        // unlimited stock from the cache.
        Integer availableStock = null;
        boolean hasEnoughStock = true;
        MenuItemInventoryReservationService inventoryService = inventoryServiceProvider == null
                ? null : inventoryServiceProvider.getIfAvailable();
        if (inventoryService != null) {
            var availability = inventoryService.availability(restaurantId, item.getMenuItemId(), item.getQuantity());
            availableStock = availability.availableQuantity();
            hasEnoughStock = availability.hasEnoughStock();
        } else {
            Object stockObj = menuItemData.get("stock");
            if (stockObj != null) {
                try {
                    availableStock = Integer.valueOf(stockObj.toString());
                    hasEnoughStock = availableStock >= item.getQuantity();
                } catch (NumberFormatException e) {
                    log.warn("⚠️ Invalid stock format for menu item {}: {}", item.getMenuItemId(), stockObj);
                    availableStock = 0;
                    hasEnoughStock = false;
                }
            } else {
                log.debug("📝 Inventory capability is disabled for menu item {}", item.getMenuItemId());
            }
        }
        
        return OrderValidationResultResponse.ItemValidationInfo.builder()
                .menuItemId(item.getMenuItemId())
                .menuItemName(canonicalName)
                .isAvailable(isAvailable)
                .actualPrice(actualPrice)
                .expectedPrice(null)
                .priceMatches(hasCanonicalPrice)
                .requestedQuantity(item.getQuantity())
                .availableStock(availableStock)
                .hasEnoughStock(hasEnoughStock)
                .build();
    }
    
    // Private helper methods
    
    private OrderValidationResultResponse.RestaurantInfo validateRestaurantInfo(
            Long restaurantId, List<OrderValidationResultResponse.ValidationError> errors) {
        
        Map<String, Object> restaurant = restaurantCacheService.getRestaurantFromCache(restaurantId);
        
        if (restaurant == null) {
            errors.add(OrderValidationResultResponse.ValidationError.builder()
                    .field("restaurantId")
                    .errorCode("RESTAURANT_NOT_FOUND")
                    .message("Restaurant không tồn tại trong cache")
                    .invalidValue(restaurantId)
                    .build());
            
            return OrderValidationResultResponse.RestaurantInfo.builder()
                    .restaurantId(restaurantId)
                    .restaurantName(null)
                    .restaurantAddress(null)
                    .restaurantPhone(null)
                    .latitude(null)
                    .longitude(null)
                    .isAvailable(false)
                    .isOpen(false)
                    .operatingHours(null)
                    .build();
        }
        
        boolean isAvailable = restaurantCacheService.isRestaurantAvailable(restaurantId);
        
        // Null-safe extraction of fields from cache
        String restaurantName = restaurant.get("name") == null
                ? null : restaurant.get("name").toString().trim();
        boolean hasCanonicalName = restaurantName != null && !restaurantName.isBlank();
        if (!hasCanonicalName) {
            errors.add(OrderValidationResultResponse.ValidationError.builder()
                    .field("restaurantName")
                    .errorCode("RESTAURANT_NAME_MISSING")
                    .message("Restaurant cache thiếu canonical name")
                    .invalidValue(null)
                    .build());
            restaurantName = null;
        }

        String restaurantAddress = restaurant.get("address") != null ?
                (String) restaurant.get("address") : null;

        String restaurantPhone = restaurant.get("phone") != null ?
                (String) restaurant.get("phone") : null;

        Long creatorId = null;
        if (restaurant.get("creatorId") != null) {
            try { creatorId = Long.valueOf(restaurant.get("creatorId").toString()); }
            catch (NumberFormatException e) { log.warn("⚠️ Invalid creatorId for restaurant {}", restaurantId); }
        }

        Long ownerPrincipalId = null;
        if (restaurant.get("ownerPrincipalId") != null) {
            try { ownerPrincipalId = Long.valueOf(restaurant.get("ownerPrincipalId").toString()); }
            catch (NumberFormatException e) { log.warn("⚠️ Invalid ownerPrincipalId for restaurant {}", restaurantId); }
        }

        Double latitude = null;
        if (restaurant.get("latitude") != null) {
            try { latitude = Double.valueOf(restaurant.get("latitude").toString()); }
            catch (NumberFormatException e) { log.warn("⚠️ Invalid latitude for restaurant {}", restaurantId); }
        }

        Double longitude = null;
        if (restaurant.get("longitude") != null) {
            try { longitude = Double.valueOf(restaurant.get("longitude").toString()); }
            catch (NumberFormatException e) { log.warn("⚠️ Invalid longitude for restaurant {}", restaurantId); }
        }

        Integer defaultPrepTimeMinutes = null;
        if (restaurant.get("defaultPrepTimeMinutes") != null) {
            try {
                defaultPrepTimeMinutes = Integer.valueOf(restaurant.get("defaultPrepTimeMinutes").toString());
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid default prep time for restaurant {}", restaurantId);
            }
        }

        String operatingHours = null;
        if (restaurant.get("openingHour") != null && restaurant.get("closingHour") != null) {
            operatingHours = restaurant.get("openingHour") + " - " + restaurant.get("closingHour");
        }
        
        return OrderValidationResultResponse.RestaurantInfo.builder()
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .restaurantAddress(restaurantAddress)
                .restaurantPhone(restaurantPhone)
                .latitude(latitude)
                .longitude(longitude)
                .defaultPrepTimeMinutes(defaultPrepTimeMinutes)
                .creatorId(creatorId)
                .ownerPrincipalId(ownerPrincipalId)
                .isAvailable(isAvailable && hasCanonicalName)
                .isOpen(isAvailable && hasCanonicalName)
                .operatingHours(operatingHours)
                .build();
    }
}
