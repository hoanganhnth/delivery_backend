package com.delivery.match_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ✅ Event được bắn khi tìm được shipper thành công
 * Dùng cho cả delivery-service (cache waiting state) và notification-service (notify shipper)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipperFoundEvent {
    
    private Long deliveryId;
    private Long orderId;
    private List<ShipperMatchResult> availableShippers;
    private LocalDateTime foundAt;
    private Integer waitingTimeoutSeconds; // Thời gian chờ shipper nhận
    private String matchingSessionId;
    
    // ✅ Additional info for notification-service
    private String restaurantName;
    private String pickupAddress;
    private String deliveryAddress;
    private Double pickupLat;
    private Double pickupLng;
    private Double deliveryLat;
    private Double deliveryLng;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipperMatchResult {
        private Long shipperId;
        private String shipperName;
        private String shipperPhone;
        private Double distanceKm;
        private Double latitude;
        private Double longitude;
        private Double rating;
        private Boolean isOnline;
    }
    
    // Constructor cho easy creation
    public ShipperFoundEvent(Long deliveryId, Long orderId, List<ShipperMatchResult> shippers) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.availableShippers = shippers;
        this.foundAt = LocalDateTime.now();
        this.waitingTimeoutSeconds = 300; // Default 5 minutes
        this.matchingSessionId = "delivery_" + deliveryId;
    }
}
