package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.request.CreatePaymentRequest;
import com.delivery.settlement_service.dto.response.PaymentOrderResponse;

import java.util.Map;

/**
 * Service quản lý luồng thanh toán qua cổng bên thứ ba
 */
public interface PaymentService {

    /**
     * Tạo giao dịch thanh toán mới
     * @return PaymentOrderResponse chứa paymentUrl để redirect
     */
    PaymentOrderResponse createPayment(CreatePaymentRequest request);

    /**
     * Xử lý callback từ cổng thanh toán (VNPay return URL, MoMo callback...)
     */
    PaymentOrderResponse handleCallback(String providerName, Map<String, String> params);

    /**
     * Query trạng thái giao dịch theo ID
     */
    PaymentOrderResponse getPaymentStatus(Long paymentId);

    /**
     * Query trạng thái giao dịch theo mã tham chiếu
     */
    PaymentOrderResponse getPaymentByRef(String paymentRef);

    /**
     * Xử lý fake payment confirm (dev/test)
     */
    PaymentOrderResponse confirmFakePayment(String paymentRef);
}
