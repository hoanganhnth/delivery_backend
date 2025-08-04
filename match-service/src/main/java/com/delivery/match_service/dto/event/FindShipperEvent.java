package com.delivery.match_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ✅ Event nhận từ Delivery Service để tự động tìm shipper theo AI Instructions
 * Match với FindShipperEvent từ Delivery Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindShipperEvent {
    
    private Long deliveryId;
    private Long orderId;
    private String pickupAddress;
    private Double pickupLat;
    private Double pickupLng;
    private String deliveryAddress;
    private Double deliveryLat;
    private Double deliveryLng;
    private LocalDateTime estimatedDeliveryTime;
    private String notes;
    private LocalDateTime createdAt;
    
    // Event metadata
    private String eventType;
    private LocalDateTime timestamp;
}
