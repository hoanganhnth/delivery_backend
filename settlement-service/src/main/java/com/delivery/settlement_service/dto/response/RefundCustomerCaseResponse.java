package com.delivery.settlement_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer-safe projection of a refund case.
 *
 * <p>Internal actor, idempotency, provider and error fields intentionally stay
 * out of this projection. The endpoint is status visibility only; it does not
 * approve, execute or mutate a refund.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCustomerCaseResponse {
    private UUID refundId;
    private Long orderId;
    private String paymentMethod;
    private String trigger;
    private String status;
    private String currency;
    private BigDecimal refundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime processedAt;
}
