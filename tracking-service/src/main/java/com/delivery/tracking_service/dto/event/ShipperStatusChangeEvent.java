package com.delivery.tracking_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ✅ Event DTO nhận từ delivery-service khi shipper thay đổi trạng thái bận/rảnh
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipperStatusChangeEvent {

    private Long shipperId;
    private String status; // "BUSY" or "AVAILABLE"
    private Long deliveryId;
    private Long orderId;
    private long timestamp;
}
