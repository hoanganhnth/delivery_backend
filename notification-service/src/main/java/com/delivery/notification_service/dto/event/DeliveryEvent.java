package com.delivery.notification_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ✅ Delivery Event DTO được nhận từ Kafka theo Backend Instructions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryEvent {

    private Long deliveryId;
    private Long orderId;
    private Long userId;
    private Long shipperId;
    private String status;
    private String shipperName;
    private String shipperPhone;
    private String eventType; // DELIVERY_STATUS_UPDATED, SHIPPER_ASSIGNED
    private LocalDateTime eventTimestamp;
}
