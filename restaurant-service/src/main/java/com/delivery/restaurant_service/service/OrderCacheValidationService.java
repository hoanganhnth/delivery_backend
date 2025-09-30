package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;

/**
 * Service để validate đơn hàng từ order-service format
 */
public interface OrderCacheValidationService {
    
    /**
     * Validate order với format từ order API
     */
    OrderValidationResultResponse validateOrderFromOrderService(OrderValidationRequest request);
    
    /**
     * Quick check restaurant availability
     */
    boolean isRestaurantAvailable(Long restaurantId);
    
    /**
     * Calculate tổng tiền từ items
     */
    Double calculateTotalFromItems(OrderValidationRequest request);
    
    /**
     * Validate individual menu item
     */
    OrderValidationResultResponse.ItemValidationInfo validateMenuItem(
            Long restaurantId, OrderValidationRequest.OrderItemRequest item);
}
