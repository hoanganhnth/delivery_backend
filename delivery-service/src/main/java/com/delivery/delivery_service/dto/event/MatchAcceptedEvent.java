package com.delivery.delivery_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ✅ Event để publish khi shipper accept delivery theo Backend Instructions
 * Event này sẽ được gửi đến Notification Service để thông báo cho customer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchAcceptedEvent {
    
    // Match information
    private String matchId;
    private Long orderId;
    private Long deliveryId;
    
    // Shipper information
    private Long shipperId;
    private String shipperName;
    private String shipperPhone;
    
    // Customer information
    private Long userId;
    private String customerName;
    private String customerPhone;
    
    // Order details
    private String restaurantName;
    private String pickupAddress;
    private String deliveryAddress;
    private BigDecimal orderValue;
    private Integer estimatedTime; // in minutes
    
    // Event metadata
    private LocalDateTime timestamp;
    private String eventType; // MATCH_ACCEPTED
    
    // Additional info
    private String notes; // Shipper's notes
}
