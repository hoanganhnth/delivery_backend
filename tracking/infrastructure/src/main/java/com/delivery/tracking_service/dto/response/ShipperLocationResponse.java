package com.delivery.tracking_service.dto.response;

import lombok.Getter;
import lombok.Setter;


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
    private String lastPing;
    private String updatedAt;
    
    // ✅ NEW: Distance field for spatial search results theo Backend Instructions
    private Double distance; // Distance in kilometers (populated by spatial queries)
}
