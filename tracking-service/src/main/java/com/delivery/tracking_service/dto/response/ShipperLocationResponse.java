package com.delivery.tracking_service.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ShipperLocationResponse {
    private Long shipperId;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double speed;
    private Double heading;
    private Boolean isOnline;
    private LocalDateTime lastPing;
    private LocalDateTime updatedAt;
    
    // ✅ NEW: Distance field for spatial search results theo Backend Instructions
    private Double distance; // Distance in kilometers (populated by spatial queries)
}
