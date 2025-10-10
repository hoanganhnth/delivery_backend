package com.delivery.delivery_service.dto.event;

import lombok.*;

import java.time.LocalDateTime;

/**
 * ✅ Event được publish khi delivery bị hủy để dừng quá trình tìm shipper
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCancelledEvent {
    
    private Long deliveryId;
    private Long orderId;
    private String reason;
    private LocalDateTime cancelledAt;
    private String cancelledBy; // USER, ADMIN, SYSTEM
    
    // Additional fields để match-service có thể dừng matching
    private String matchingSessionId; // Unique ID cho mỗi session tìm shipper
    private boolean stopMatching;     // Flag để dừng quá trình matching
    
    public DeliveryCancelledEvent(Long deliveryId, Long orderId, String reason) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.reason = reason;
        this.cancelledAt = LocalDateTime.now();
        this.stopMatching = true;
        this.matchingSessionId = "delivery_" + deliveryId;
    }
}
