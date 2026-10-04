package com.delivery.settlement_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only admin projection of a refund decision.
 *
 * <p>The projection deliberately excludes event payloads and provider secrets;
 * it is for queue visibility, not approval or provider execution.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCaseResponse {
    private UUID refundId;
    private UUID eventId;
    private String idempotencyKey;
    private Long orderId;
    private Long userId;
    private Long userPrincipalId;
    private Long restaurantId;
    private String previousOrderStatus;
    private String currentOrderStatus;
    private String paymentMethod;
    private String trigger;
    private String component;
    private String status;
    private String currency;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private BigDecimal capturedAmount;
    private BigDecimal refundAmount;
    private String actorSource;
    private Long actorId;
    private String reason;
    private String providerReference;
    private String lastError;
    private int attempts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime processedAt;
}
