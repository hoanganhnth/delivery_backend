package com.delivery.tracking_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
