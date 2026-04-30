package com.delivery.settlement_service.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả từ PaymentProvider.createPayment()
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {

    private boolean success;
    private String paymentUrl;               // URL để redirect người dùng
    private String providerTransactionId;    // Mã giao dịch từ cổng
    private String errorMessage;             // Lỗi nếu có

    public static PaymentResult success(String paymentUrl, String providerTxnId) {
        return PaymentResult.builder()
                .success(true)
                .paymentUrl(paymentUrl)
                .providerTransactionId(providerTxnId)
                .build();
    }

    public static PaymentResult failure(String errorMessage) {
        return PaymentResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
