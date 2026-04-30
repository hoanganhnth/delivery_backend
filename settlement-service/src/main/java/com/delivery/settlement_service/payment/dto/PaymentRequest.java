package com.delivery.settlement_service.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Input cho PaymentProvider.createPayment()
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private String paymentRef;       // Mã tham chiếu duy nhất
    private BigDecimal amount;       // Số tiền
    private String currency;         // VND
    private String orderInfo;        // Mô tả giao dịch
    private String returnUrl;        // URL redirect sau thanh toán
    private String ipAddress;        // IP khách hàng
    private String locale;           // vi / en
}
