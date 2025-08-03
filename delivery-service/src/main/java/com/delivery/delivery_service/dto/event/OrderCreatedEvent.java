package com.delivery.delivery_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ✅ Event DTO nhận từ Order Service qua Kafka theo Backend Instructions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    
    // Order basic info
    private Long orderId;
    private Long userId;
    private Long restaurantId;
    private String status;
    
    // Financial info
    private BigDecimal subtotalPrice;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalPrice;
    private String paymentMethod;
    
    // Delivery location info
    private String deliveryAddress;
    private Double deliveryLat;
    private Double deliveryLng;
    
    // Restaurant info
    private String restaurantName;
    private String restaurantAddress;
    private String restaurantPhone;
    
    // Customer info
    private String customerName;
    private String customerPhone;
    private String notes;
    
    // Timestamps
    private LocalDateTime createdAt;
    private Long creatorId;
    
    // Event metadata
    private String eventType;
    private LocalDateTime eventTimestamp;
}
