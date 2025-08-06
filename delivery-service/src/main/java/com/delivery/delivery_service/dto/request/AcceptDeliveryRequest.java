package com.delivery.delivery_service.dto.request;

import lombok.Data;

/**
 * ✅ Request DTO cho shipper accept/reject delivery theo Backend Instructions
 */
@Data
public class AcceptDeliveryRequest {
    
    private Long orderId; // Order ID to accept/reject
    
    private String action; // "ACCEPT" hoặc "REJECT"
    
    private String notes; // Optional notes from shipper (required for reject)
    
    private String rejectReason; // Reason for rejection (if action = REJECT)
    
    private Double estimatedPickupTime; // Shipper's estimated pickup time in minutes (for ACCEPT)
    
    private Double currentLat; // Shipper's current latitude
    
    private Double currentLng; // Shipper's current longitude
}
