package com.delivery.delivery_service.dto.request;

import lombok.Data;

/**
 * ✅ Request DTO cho shipper accept delivery theo Backend Instructions
 */
@Data
public class AcceptDeliveryRequest {
    
    private Long orderId; // Order ID to accept
    
    private String notes; // Optional notes from shipper
    
    private Double estimatedPickupTime; // Shipper's estimated pickup time in minutes
    
    private Double currentLat; // Shipper's current latitude
    
    private Double currentLng; // Shipper's current longitude
}
