package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.request.ValidateOrderRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResponse;
import com.delivery.restaurant_service.service.OrderValidationService;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation tạm thời trong restaurant-service
 * Thiết kế để dễ dàng tách thành Catalog Service độc lập
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderValidationServiceImpl implements OrderValidationService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestaurantCacheService restaurantCacheService;
    private final ObjectMapper objectMapper;
    
    // Redis Keys - thiết kế để tương thích với future Catalog Service
    private static final String RESTAURANT_KEY_PREFIX = "catalog:restaurant:";
    private static final String MENU_ITEM_KEY_PREFIX = "catalog:menu_item:";
    private static final String RESTAURANT_MENU_KEY_PREFIX = "catalog:restaurant_menu:";
    
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
        try {
            String menuItemKey = MENU_ITEM_KEY_PREFIX + menuItemId;
            Map<String, Object> menuItem = (Map<String, Object>) redisTemplate.opsForValue().get(menuItemKey);
            
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
    
    @Override
    public Double calculateOrderTotal(ValidateOrderRequest request) {
        double total = 0.0;
        
        for (ValidateOrderRequest.OrderItemValidationRequest item : request.getItems()) {
            Double itemPrice = getMenuItemPrice(item.getMenuItemId());
            if (itemPrice != null) {
                total += itemPrice * item.getQuantity();
            }
        }
        
        return total;
    }
    
    @Override
    public boolean validateRestaurantOperatingHours(Long restaurantId) {
        try {
            String restaurantKey = RESTAURANT_KEY_PREFIX + restaurantId;
            Map<String, Object> restaurant = (Map<String, Object>) redisTemplate.opsForValue().get(restaurantKey);
            
            if (restaurant == null) {
                return false;
            }
            
            Boolean isOpen = (Boolean) restaurant.get("isOpen");
            if (Boolean.FALSE.equals(isOpen)) {
                return false;
            }
            
            // Kiểm tra giờ mở cửa
            String openTime = (String) restaurant.get("openTime");
            String closeTime = (String) restaurant.get("closeTime");
            
            if (openTime != null && closeTime != null) {
                LocalTime now = LocalTime.now();
                LocalTime open = LocalTime.parse(openTime, DateTimeFormatter.ofPattern("HH:mm"));
                LocalTime close = LocalTime.parse(closeTime, DateTimeFormatter.ofPattern("HH:mm"));
                
                return now.isAfter(open) && now.isBefore(close);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("💥 Error validating restaurant operating hours: {}", e.getMessage());
            return false;
        }
    }
    
    // Private helper methods
    
    private OrderValidationResponse.RestaurantValidationInfo validateRestaurantInfo(
            Long restaurantId, List<OrderValidationResponse.ValidationError> errors) {
        
        String restaurantKey = RESTAURANT_KEY_PREFIX + restaurantId;
        Map<String, Object> restaurant = (Map<String, Object>) redisTemplate.opsForValue().get(restaurantKey);
        
        if (restaurant == null) {
            errors.add(OrderValidationResponse.ValidationError.builder()
                    .field("restaurantId")
                    .errorCode("RESTAURANT_NOT_FOUND")
                    .message("Restaurant không tồn tại")
                    .invalidValue(restaurantId)
                    .build());
            return null;
        }
        
        Boolean isOpen = validateRestaurantOperatingHours(restaurantId);
        
        return OrderValidationResponse.RestaurantValidationInfo.builder()
                .restaurantId(restaurantId)
                .restaurantName((String) restaurant.get("name"))
                .isOpen(isOpen)
                .operatingHours(restaurant.get("openTime") + " - " + restaurant.get("closeTime"))
                .isAvailable((Boolean) restaurant.get("isAvailable"))
                .build();
    }
    
    private Double validateOrderItem(ValidateOrderRequest.OrderItemValidationRequest item, 
                                   Long restaurantId, List<OrderValidationResponse.ValidationError> errors) {
        
        Double itemPrice = getMenuItemPrice(item.getMenuItemId());
        
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
        
        // Validate menu item
        if (!validateMenuItem(restaurantId, item.getMenuItemId(), item.getQuantity())) {
            errors.add(OrderValidationResponse.ValidationError.builder()
                    .field("menuItem")
                    .errorCode("MENU_ITEM_INVALID")
                    .message("Món ăn không khả dụng hoặc không đủ số lượng")
                    .invalidValue(item.getMenuItemId())
                    .build());
        }
        
        return itemPrice * item.getQuantity();
    }
    
    private Double getMenuItemPrice(Long menuItemId) {
        try {
            String menuItemKey = MENU_ITEM_KEY_PREFIX + menuItemId;
            Map<String, Object> menuItem = (Map<String, Object>) redisTemplate.opsForValue().get(menuItemKey);
            
            if (menuItem != null && menuItem.get("price") != null) {
                return Double.valueOf(menuItem.get("price").toString());
            }
            
            return null;
        } catch (Exception e) {
            log.error("💥 Error getting menu item price: {}", e.getMessage());
            return null;
        }
    }
}
