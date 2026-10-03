package com.delivery.tracking_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import com.delivery.identity.contracts.SimulationContext;

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
    private SimulationContext simulationContext;

    public ShipperLocationUpdatedEvent(Long shipperId, Double latitude, Double longitude, Boolean isOnline,
                                       long timestamp, UUID eventId, Long deliveryId, Double accuracy,
                                       Double speed, Double heading, String source) {
        this(shipperId, latitude, longitude, isOnline, timestamp, eventId, deliveryId, accuracy,
                speed, heading, source, SimulationContext.real());
    }

    public ShipperLocationUpdatedEvent(Long shipperId, Double latitude, Double longitude,
                                       Boolean isOnline, long timestamp) {
        this(shipperId, latitude, longitude, isOnline, timestamp, UUID.randomUUID(),
                null, null, null, null, "UNKNOWN");
    }
}
