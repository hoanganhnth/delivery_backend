package com.delivery.notification_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ✅ Order Event DTO được nhận từ Kafka theo Backend Instructions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    private Long orderId;
    private Long userId;
    private Long restaurantId;
    private String status;
    private String restaurantName;
    private String customerName;
    private String customerPhone;
    private String eventType; // ORDER_CREATED, ORDER_STATUS_UPDATED
    private LocalDateTime eventTimestamp;
}
