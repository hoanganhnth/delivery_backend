package com.delivery.restaurant_service.dto.request;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidateOrderRequest {
    private Long restaurantId;
    private Long userId;
    private List<OrderItemValidationRequest> items;
    private Double expectedTotal;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemValidationRequest {
        private Long menuItemId;
        private Integer quantity;
        private Double expectedPrice;
        private String specialInstructions;
    }
}
