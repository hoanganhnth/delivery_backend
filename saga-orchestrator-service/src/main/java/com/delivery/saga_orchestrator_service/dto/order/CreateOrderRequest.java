package com.delivery.saga_orchestrator_service.dto.order;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    private Long restaurantId;
    private String restaurantName;
    private String restaurantAddress;
    private String restaurantPhone;
    private String deliveryAddress;
    private Double restaurantLat;
    private Double restaurantLng;
    private Double deliveryLat;
    private Double deliveryLng;
    private String customerName;
    private String customerPhone;
    private String paymentMethod; // COD or ONLINE
    private String notes;
    private List<OrderItemRequest> items;

    @Setter
    @Getter
    public static class OrderItemRequest {
        private Long menuItemId;
        private String menuItemName;
        private Integer quantity;
        private BigDecimal price;
        private String notes;


    }
}
