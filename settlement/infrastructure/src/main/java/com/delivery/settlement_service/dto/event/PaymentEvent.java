package com.delivery.settlement_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * compatible with order-service PaymentEvent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private String status; // COMPLETED, FAILED, PENDING
    private Double amount;
    private String paymentMethod;
    private String transactionId;
    private LocalDateTime processedAt;
    private String failureReason;
}
