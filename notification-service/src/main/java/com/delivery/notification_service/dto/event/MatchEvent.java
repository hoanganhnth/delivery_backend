package com.delivery.notification_service.dto.event;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * ✅ Match Event DTO để nhận events từ Match Service theo Backend Instructions
 */
@Data
public class MatchEvent {
    
    private Long matchId;
    private Long orderId;
    private Long userId;
    private Long shipperId;
    private String shipperName;
    private String shipperPhone;
    private String status; // REQUESTED, ACCEPTED, REJECTED, FOUND
    private Double latitude;
    private Double longitude;
    private Double distance; // Distance in km
    private String pickupAddress;
    private String deliveryAddress;
    private Double estimatedPrice;
    private Integer estimatedTime; // Minutes
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private String reason; // Lý do reject (nếu có)
    
    // Additional data for notifications
    private String restaurantName;
    private String customerName;
    private String customerPhone;
    private Double orderValue;
}
