package com.delivery.delivery_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ✅ Event gửi đến Match Service để tìm shipper phù hợp theo AI Instructions
 * Được publish khi delivery được tạo thành công với status PENDING
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
    private String eventType = "FIND_SHIPPER_REQUESTED";
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // Constructor cho business logic
    public FindShipperEvent(Long deliveryId, Long orderId, String pickupAddress, 
                           Double pickupLat, Double pickupLng, String deliveryAddress,
                           Double deliveryLat, Double deliveryLng, LocalDateTime estimatedDeliveryTime,
                           String notes, LocalDateTime createdAt) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.pickupAddress = pickupAddress;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.deliveryAddress = deliveryAddress;
        this.deliveryLat = deliveryLat;
        this.deliveryLng = deliveryLng;
        this.estimatedDeliveryTime = estimatedDeliveryTime;
        this.notes = notes;
        this.createdAt = createdAt;
        this.eventType = "FIND_SHIPPER_REQUESTED";
        this.timestamp = LocalDateTime.now();
    }
}
