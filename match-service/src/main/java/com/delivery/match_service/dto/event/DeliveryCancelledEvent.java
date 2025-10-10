package com.delivery.match_service.dto.event;

import lombok.*;

import java.time.LocalDateTime;

/**
 * ✅ Event nhận từ Delivery Service khi delivery bị hủy để dừng matching
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
}
