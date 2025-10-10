package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.request.ValidateOrderRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResponse;
import com.delivery.restaurant_service.service.OrderValidationService;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
//need remove 
/**
 * Order validation service sử dụng RestaurantCacheService
 * Không direct access Redis, tuân thủ service layer architecture 
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderValidationServiceImpl implements OrderValidationService {
    
    private final RestaurantCacheService restaurantCacheService;
    
    @Override
    public OrderValidationResponse validateOrder(ValidateOrderRequest request) {
        log.info("🔍 Validating order for restaurant: {}", request.getRestaurantId());
        
        List<OrderValidationResponse.ValidationError> errors = new ArrayList<>();
        OrderValidationResponse.RestaurantValidationInfo restaurantInfo = 
                validateRestaurantInfo(request.getRestaurantId(), errors);
        
        // Validate từng món
        Double calculatedTotal = 0.0;
        for (ValidateOrderRequest.OrderItemValidationRequest item : request.getItems()) {
            calculatedTotal += validateOrderItem(item, request.getRestaurantId(), errors);
        }
        
        // So sánh tổng tiền
        if (request.getExpectedTotal() != null && 
            !request.getExpectedTotal().equals(calculatedTotal)) {
            errors.add(OrderValidationResponse.ValidationError.builder()
                    .field("expectedTotal")
                    .errorCode("TOTAL_MISMATCH")
                    .message("Tổng tiền không khớp")
                    .invalidValue(Map.of(
                            "expected", request.getExpectedTotal(),
                            "calculated", calculatedTotal))
                    .build());
        }
        
        boolean isValid = errors.isEmpty();
        String message = isValid ? "Order validation successful" : "Order validation failed";
        
        return OrderValidationResponse.builder()
                .isValid(isValid)
                .message(message)
                .calculatedTotal(calculatedTotal)
                .errors(errors)
                .restaurantInfo(restaurantInfo)
                .build();
    }
    
    @Override
    public boolean validateMenuItem(Long restaurantId, Long menuItemId, Integer quantity) {
        log.debug("🔍 Validating menu item: {} for restaurant: {} with quantity: {}", 
                menuItemId, restaurantId, quantity);
        return restaurantCacheService.isMenuItemAvailable(restaurantId, menuItemId, quantity);
    }
    
    @Override
    public Double calculateOrderTotal(ValidateOrderRequest request) {
        double total = 0.0;
        
        for (ValidateOrderRequest.OrderItemValidationRequest item : request.getItems()) {
            Double itemPrice = restaurantCacheService.getMenuItemPrice(item.getMenuItemId());
            if (itemPrice != null) {
                total += itemPrice * item.getQuantity();
            }
        }
        
        return total;
    }
    
    @Override
    public boolean validateRestaurantOperatingHours(Long restaurantId) {
        log.debug("🔍 Validating operating hours for restaurant: {}", restaurantId);
        return restaurantCacheService.isRestaurantAvailable(restaurantId);
    }
    
    // Private helper methods
    
    private OrderValidationResponse.RestaurantValidationInfo validateRestaurantInfo(
            Long restaurantId, List<OrderValidationResponse.ValidationError> errors) {
        
        Map<String, Object> restaurant = restaurantCacheService.getRestaurantFromCache(restaurantId);
        
        if (restaurant == null) {
            errors.add(OrderValidationResponse.ValidationError.builder()
                    .field("restaurantId")
                    .errorCode("RESTAURANT_NOT_FOUND")
                    .message("Restaurant không tồn tại")
                    .invalidValue(restaurantId)
                    .build());
            return null;
        }
        
        Boolean isAvailable = restaurantCacheService.isRestaurantAvailable(restaurantId);
        
        return OrderValidationResponse.RestaurantValidationInfo.builder()
                .restaurantId(restaurantId)
                .restaurantName((String) restaurant.get("name"))
                .isOpen(isAvailable)
                .operatingHours(restaurant.get("openTime") + " - " + restaurant.get("closeTime"))
                .isAvailable((Boolean) restaurant.get("isAvailable"))
                .build();
    }
    
    private Double validateOrderItem(ValidateOrderRequest.OrderItemValidationRequest item, 
                                   Long restaurantId, List<OrderValidationResponse.ValidationError> errors) {
        
        Double itemPrice = restaurantCacheService.getMenuItemPrice(item.getMenuItemId());
        
        if (itemPrice == null) {
            errors.add(OrderValidationResponse.ValidationError.builder()
                    .field("menuItemId")
                    .errorCode("MENU_ITEM_NOT_FOUND")
                    .message("Món ăn không tồn tại")
                    .invalidValue(item.getMenuItemId())
                    .build());
            return 0.0;
        }
        
        // Kiểm tra giá có khớp không
        if (item.getExpectedPrice() != null && !item.getExpectedPrice().equals(itemPrice)) {
            errors.add(OrderValidationResponse.ValidationError.builder()
                    .field("expectedPrice")
                    .errorCode("PRICE_MISMATCH")
                    .message("Giá món ăn đã thay đổi")
                    .invalidValue(Map.of(
                            "menuItemId", item.getMenuItemId(),
                            "expected", item.getExpectedPrice(),
                            "actual", itemPrice))
                    .build());
        }
        
        // Validate menu item availability
        if (!restaurantCacheService.isMenuItemAvailable(restaurantId, item.getMenuItemId(), item.getQuantity())) {
            errors.add(OrderValidationResponse.ValidationError.builder()
                    .field("menuItem")
                    .errorCode("MENU_ITEM_INVALID")
                    .message("Món ăn không khả dụng hoặc không đủ số lượng")
                    .invalidValue(item.getMenuItemId())
                    .build());
        }
        
        return itemPrice * item.getQuantity();
    }
}
