package com.delivery.settlement_service.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả xác minh thanh toán từ callback/IPN
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerifyResult {

    private boolean verified;                // Checksum/signature hợp lệ
    private boolean paymentSuccess;          // Giao dịch thành công (response code = 00)
    private String paymentRef;               // Mã tham chiếu
    private String providerTransactionId;    // Mã giao dịch từ cổng
    private String responseCode;             // Mã phản hồi (00 = success)
    private String message;                  // Thông điệp
    private String rawPayload;               // JSON raw từ cổng

    public static PaymentVerifyResult success(String paymentRef, String providerTxnId, String raw) {
        return PaymentVerifyResult.builder()
                .verified(true)
                .paymentSuccess(true)
                .paymentRef(paymentRef)
                .providerTransactionId(providerTxnId)
                .responseCode("00")
                .message("Success")
                .rawPayload(raw)
                .build();
    }

    public static PaymentVerifyResult failed(String paymentRef, String responseCode, String message, String raw) {
        return PaymentVerifyResult.builder()
                .verified(true)
                .paymentSuccess(false)
                .paymentRef(paymentRef)
                .responseCode(responseCode)
                .message(message)
                .rawPayload(raw)
                .build();
    }

    public static PaymentVerifyResult invalidSignature(String message) {
        return PaymentVerifyResult.builder()
                .verified(false)
                .paymentSuccess(false)
                .message(message)
                .build();
    }
}
