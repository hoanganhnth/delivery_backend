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
    
    // Restaurant information
    private String restaurantName;   // Tên nhà hàng
    private String pickupAddress;
    private Double pickupLat;
    private Double pickupLng;
    
    // Delivery information
    private String deliveryAddress;
    private Double deliveryLat;
    private Double deliveryLng;
    private LocalDateTime estimatedDeliveryTime;
    private String notes;
    private LocalDateTime createdAt;
    
    // Event metadata
    private String eventType;
    private LocalDateTime timestamp;
    
    // Saga Match Configuration (Syncing timeout & retry from Orchestrator)
    private Integer maxRetryAttempts;
    private Integer initialDelaySeconds;
    private Integer maxDelaySeconds;
    private Double backoffMultiplier;
    
    // ✅ NEW: Excluded shipper IDs (shippers who already rejected this order)
    private java.util.List<Long> excludedShipperIds;
}
