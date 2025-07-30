package com.delivery.saga_orchestrator_service.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderResponse {

    private Long id;
    private Long userId;
    private Long restaurantId;
    private String restaurantName;
    private String restaurantAddress;
    private String restaurantPhone;
    private Double restaurantLat;
    private Double restaurantLng;
    private Long shipperId;
    private BigDecimal subtotalPrice;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalPrice;
    private String status;
    private String paymentMethod;
    private String deliveryAddress;
    private Double deliveryLat;
    private Double deliveryLng;
    private String customerName;
    private String customerPhone;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
