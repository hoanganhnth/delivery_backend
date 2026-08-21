package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.service.OrderCacheValidationService;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation validation service cho order format từ order-service
 * Sử dụng RestaurantCacheService để lấy data từ Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCacheValidationServiceImpl implements OrderCacheValidationService {
    
    private final RestaurantCacheService restaurantCacheService;
    
    @Override
    public OrderValidationResultResponse validateOrderFromOrderService(OrderValidationRequest request) {
        log.info("🔍 Validating order from order-service for restaurant: {}", request.getRestaurantId());
        
        List<OrderValidationResultResponse.ValidationError> errors = new ArrayList<>();
        List<OrderValidationResultResponse.ItemValidationInfo> itemValidations = new ArrayList<>();
        
        // 1. Validate restaurant info
        OrderValidationResultResponse.RestaurantInfo restaurantInfo = 
                validateRestaurantInfo(request.getRestaurantId(), errors);
        
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
        
        boolean isValid = errors.isEmpty() && restaurantInfo != null && restaurantInfo.getIsAvailable();
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
        
        // Kiểm tra stock với null-safe operation - Note: current cache structure doesn't include stock
        // but we keep this for future compatibility
        Integer availableStock = null;
        boolean hasEnoughStock = true;
        
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
            // Không có thông tin stock trong cache hiện tại, assume có đủ hàng
            log.debug("📝 No stock information in cache for menu item {}, assuming sufficient stock", item.getMenuItemId());
            availableStock = null; // Unknown stock
            hasEnoughStock = true; // Assume available if no stock tracking
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
                .creatorId(creatorId)
                .ownerPrincipalId(ownerPrincipalId)
                .isAvailable(isAvailable && hasCanonicalName)
                .isOpen(isAvailable && hasCanonicalName)
                .operatingHours(operatingHours)
                .build();
    }
}
