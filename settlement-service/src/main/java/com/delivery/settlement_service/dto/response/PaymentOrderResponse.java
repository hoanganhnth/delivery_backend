package com.delivery.settlement_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response trả về thông tin giao dịch thanh toán
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {

    private Long id;
    private String paymentRef;
    private Long entityId;
    private String entityType;
    private String provider;
    private BigDecimal amount;
    private String currency;
    private String purpose;
    private String status;
    private String paymentUrl;
    private String providerTransactionId;
    private Long settlementTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;
}
