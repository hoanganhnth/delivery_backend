package com.delivery.tracking_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * ✅ Event DTO gửi qua Kafka khi shipper cập nhật vị trí
 * Chỉ chứa dữ liệu tối thiểu cần thiết cho match-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipperLocationUpdatedEvent {
    
    private Long shipperId;
    private Double latitude;
    private Double longitude;
    private Boolean isOnline;
    private long timestamp;  // epoch millis
    private UUID eventId;
    private Long deliveryId;
    private Double accuracy;
    private Double speed;
    private Double heading;
    private String source;

    public ShipperLocationUpdatedEvent(Long shipperId, Double latitude, Double longitude,
                                       Boolean isOnline, long timestamp) {
        this(shipperId, latitude, longitude, isOnline, timestamp, UUID.randomUUID(),
                null, null, null, null, "UNKNOWN");
    }
}
