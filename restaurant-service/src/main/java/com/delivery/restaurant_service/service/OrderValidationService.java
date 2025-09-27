package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.ValidateOrderRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResponse;

/**
 * Service interface để validate order với restaurant data
 * Thiết kế để dễ dàng tách thành Catalog Service riêng biệt
 */
public interface OrderValidationService {
    
    /**
     * Validate toàn bộ order với restaurant data
     */
    OrderValidationResponse validateOrder(ValidateOrderRequest request);
    
    /**
     * Validate từng menu item có tồn tại và available không
     */
    boolean validateMenuItem(Long restaurantId, Long menuItemId, Integer quantity);
    
    /**
     * Tính tổng giá trị order dựa trên Redis data
     */
    Double calculateOrderTotal(ValidateOrderRequest request);
    
    /**
     * Validate thời gian mở cửa của restaurant
     */
    boolean validateRestaurantOperatingHours(Long restaurantId);
}
