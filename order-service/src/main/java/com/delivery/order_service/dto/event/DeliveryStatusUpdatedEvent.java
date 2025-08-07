package com.delivery.order_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ✅ Delivery Status Updated Event DTO theo AI Coding Instructions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatusUpdatedEvent {
    
    private Long deliveryId;
    private Long orderId;
    private Long shipperId;
    private String status; // ASSIGNED, IN_PROGRESS, DELIVERED, CANCELLED
    private String previousStatus;
    private LocalDateTime updatedAt;
    private String notes;
    private Double currentLat;
    private Double currentLng;
    private LocalDateTime estimatedDeliveryTime;
}
