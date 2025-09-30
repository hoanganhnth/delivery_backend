package com.delivery.restaurant_service.dto.request;

import lombok.*;

import java.util.List;

/**
 * Request DTO để validate đơn hàng từ order-service
 * Mapping với format từ order API
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderValidationRequest {
    
    // Restaurant info
    private Long restaurantId;
    private String restaurantName;
    private String restaurantAddress;
    private String restaurantPhone;
    
    // Delivery info
    private String deliveryAddress;
    private Double deliveryLng;
    private Double deliveryLat;
    
    // Pickup info
    private Double pickupLng;
    private Double pickupLat;
    
    // Customer info
    private String customerName;
    private String customerPhone;
    
    // Order info
    private String paymentMethod;
    private String notes;
    private List<OrderItemRequest> items;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemRequest {
        private Long menuItemId;
        private String menuItemName;
        private Integer quantity;
        private Double price;
        private String notes;
    }
}
