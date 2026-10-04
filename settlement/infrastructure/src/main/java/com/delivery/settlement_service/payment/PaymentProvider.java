package com.delivery.settlement_service.payment;

import com.delivery.settlement_service.payment.dto.PaymentRequest;
import com.delivery.settlement_service.payment.dto.PaymentResult;
import com.delivery.settlement_service.payment.dto.PaymentVerifyResult;

import java.util.Map;

/**
 * Strategy interface cho các cổng thanh toán.
 * Mỗi cổng (VNPay, MoMo, ZaloPay, Fake...) implement interface này.
 */
public interface PaymentProvider {

    /**
     * Tên nhà cung cấp (VNPAY, MOMO, FAKE...)
     */
    String getProviderName();

    /**
     * Tạo giao dịch thanh toán và trả về URL redirect
     */
    PaymentResult createPayment(PaymentRequest request);

    /**
     * Xác minh callback/IPN từ cổng thanh toán
     * @param params Map các tham số từ callback (query params hoặc form data)
     */
    PaymentVerifyResult verifyPayment(Map<String, String> params);
}
