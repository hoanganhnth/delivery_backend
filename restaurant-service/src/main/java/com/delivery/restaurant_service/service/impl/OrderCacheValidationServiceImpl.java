package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
import com.delivery.restaurant_service.service.OrderCacheValidationService;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
                errors.add(OrderValidationResultResponse.ValidationError.builder()
                        .field("price")
                        .errorCode("PRICE_MISMATCH")
                        .message("Giá món " + item.getMenuItemName() + " đã thay đổi")
                        .invalidValue(Map.of(
                                "expected", itemValidation.getExpectedPrice(),
                                "actual", itemValidation.getActualPrice()))
                        .build());
            }
            
            if (!itemValidation.getHasEnoughStock()) {
                errors.add(OrderValidationResultResponse.ValidationError.builder()
                        .field("stock")
                        .errorCode("INSUFFICIENT_STOCK")
                        .message("Không đủ hàng cho món " + item.getMenuItemName())
                        .invalidValue(Map.of(
                                "requested", itemValidation.getRequestedQuantity(),
                                "available", itemValidation.getAvailableStock()))
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
        
        // Lấy thông tin từ cache
        Map<String, Object> menuItemData = restaurantCacheService.getMenuItemFromCache(item.getMenuItemId());
        Double actualPrice = restaurantCacheService.getMenuItemPrice(item.getMenuItemId());
        boolean isAvailable = restaurantCacheService.isMenuItemAvailable(
                restaurantId, item.getMenuItemId(), item.getQuantity());
        
        // Kiểm tra price match
        boolean priceMatches = actualPrice != null && actualPrice.equals(item.getPrice());
        
        // Kiểm tra stock
        Integer availableStock = null;
        boolean hasEnoughStock = true;
        
        if (menuItemData != null && menuItemData.get("stock") != null) {
            availableStock = Integer.valueOf(menuItemData.get("stock").toString());
            hasEnoughStock = availableStock >= item.getQuantity();
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
        }
        
        boolean isAvailable = restaurantCacheService.isRestaurantAvailable(restaurantId);
        
        String operatingHours = "N/A";
        if (restaurant.get("openingHour") != null && restaurant.get("closingHour") != null) {
            operatingHours = restaurant.get("openingHour") + " - " + restaurant.get("closingHour");
        }
        
        return OrderValidationResultResponse.RestaurantInfo.builder()
                .restaurantId(restaurantId)
                .restaurantName((String) restaurant.get("name"))
                .isAvailable(isAvailable)
                .isOpen(isAvailable) // Simplified for now
                .operatingHours(operatingHours)
                .build();
    }
}
