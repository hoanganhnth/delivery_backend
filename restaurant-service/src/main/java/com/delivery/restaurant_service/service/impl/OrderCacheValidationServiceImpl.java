package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
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
                        .message("Món ăn " + item.getMenuItemName() + " không khả dụng")
                        .invalidValue(item.getMenuItemId())
                        .build());
            }
            
            if (!itemValidation.getPriceMatches()) {
                // Create map with null-safe values for Map.of()
                Map<String, Object> priceInfo = new HashMap<>();
                priceInfo.put("expected", itemValidation.getExpectedPrice());
                priceInfo.put("actual", itemValidation.getActualPrice());
                
                errors.add(OrderValidationResultResponse.ValidationError.builder()
                        .field("price")
                        .errorCode("PRICE_MISMATCH")
                        .message("Giá món " + item.getMenuItemName() + " đã thay đổi")
                        .invalidValue(priceInfo)
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
                        .message("Không đủ hàng cho món " + item.getMenuItemName())
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
    
    @Override
    public boolean isRestaurantAvailable(Long restaurantId) {
        return restaurantCacheService.isRestaurantAvailable(restaurantId);
    }
    
    @Override
    public Double calculateTotalFromItems(OrderValidationRequest request) {
        double total = 0.0;
        
        for (OrderValidationRequest.OrderItemRequest item : request.getItems()) {
            Double actualPrice = restaurantCacheService.getMenuItemPrice(item.getMenuItemId());
            if (actualPrice != null) {
                total += actualPrice * item.getQuantity();
            }
        }
        
        return total;
    }
    
    @Override
    public OrderValidationResultResponse.ItemValidationInfo validateMenuItem(
            Long restaurantId, OrderValidationRequest.OrderItemRequest item) {
        
        // Lấy thông tin từ cache với null check
        Map<String, Object> menuItemData = restaurantCacheService.getMenuItemFromCache(item.getMenuItemId());
        
        // Kiểm tra menuItemData có tồn tại không
        if (menuItemData == null) {
            log.warn("⚠️ Menu item {} not found in cache", item.getMenuItemId());
            return OrderValidationResultResponse.ItemValidationInfo.builder()
                    .menuItemId(item.getMenuItemId())
                    .menuItemName(item.getMenuItemName())
                    .isAvailable(false)
                    .actualPrice(null)
                    .expectedPrice(item.getPrice())
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
        boolean belongsToRestaurant = true;
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
        
        // Null-safe status check
        boolean statusAvailable = true;
        Object statusObj = menuItemData.get("status");
        if (statusObj != null) {
            String statusStr = statusObj.toString();
            statusAvailable = !"SOLD_OUT".equals(statusStr) && !"UNAVAILABLE".equals(statusStr);
        }
        
        // Overall availability check
        boolean isAvailable = belongsToRestaurant && statusAvailable && 
                             restaurantCacheService.isRestaurantAvailable(restaurantId);
        
        // Kiểm tra price match với null-safe operation
        boolean priceMatches = actualPrice != null && actualPrice.equals(item.getPrice());
        
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
                .menuItemName(item.getMenuItemName())
                .isAvailable(isAvailable)
                .actualPrice(actualPrice)
                .expectedPrice(item.getPrice())
                .priceMatches(priceMatches)
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
            
            // Return default info nếu restaurant không tồn tại
            return OrderValidationResultResponse.RestaurantInfo.builder()
                    .restaurantId(restaurantId)
                    .restaurantName("Unknown Restaurant")
                    .isAvailable(false)
                    .isOpen(false)
                    .operatingHours("N/A")
                    .build();
        }
        
        boolean isAvailable = restaurantCacheService.isRestaurantAvailable(restaurantId);
        
        // Null-safe operation cho operating hours
        String operatingHours = "N/A";
        if (restaurant.get("openingHour") != null && restaurant.get("closingHour") != null) {
            operatingHours = restaurant.get("openingHour") + " - " + restaurant.get("closingHour");
        }
        
        // Null-safe operation cho restaurant name
        String restaurantName = restaurant.get("name") != null ? 
                (String) restaurant.get("name") : "Unknown Restaurant";
        
        return OrderValidationResultResponse.RestaurantInfo.builder()
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .isAvailable(isAvailable)
                .isOpen(isAvailable) // Simplified for now
                .operatingHours(operatingHours)
                .build();
    }
}
