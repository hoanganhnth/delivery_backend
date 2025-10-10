package com.delivery.delivery_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ✅ Event DTO nhận từ Kafka khi order bị hủy từ Order Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    
    // Order basic info
    private Long orderId;
    private Long userId;
    private Long restaurantId;
    private String previousStatus;
    private String currentStatus; // CANCELLED
    
    // Cancellation info
    private String cancelReason;
    private Long cancelledBy; // userId who cancelled
    private LocalDateTime cancelledAt;
    
    // Delivery related
    private Long shipperId; // null if no shipper assigned
    private Boolean hasActiveDelivery;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
