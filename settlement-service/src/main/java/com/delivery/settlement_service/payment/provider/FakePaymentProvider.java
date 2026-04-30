package com.delivery.settlement_service.payment.provider;

import com.delivery.settlement_service.payment.PaymentProvider;
import com.delivery.settlement_service.payment.dto.PaymentRequest;
import com.delivery.settlement_service.payment.dto.PaymentResult;
import com.delivery.settlement_service.payment.dto.PaymentVerifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fake Payment Provider — Auto-approve mọi giao dịch.
 * Dùng cho môi trường dev/test mà không cần cổng thanh toán thật.
 */
@Component
@Slf4j
public class FakePaymentProvider implements PaymentProvider {

    @Value("${server.port:8095}")
    private int serverPort;

    @Override
    public String getProviderName() {
        return "FAKE";
    }

    @Override
    public PaymentResult createPayment(PaymentRequest request) {
        log.info("🎭 [FAKE] Creating fake payment: ref={}, amount={}", request.getPaymentRef(), request.getAmount());

        // Tạo URL confirm giả — client gọi URL này để auto-approve
        String confirmUrl = "http://localhost:" + serverPort
                + "/api/settlement/payments/fake-confirm/" + request.getPaymentRef();

        log.info("✅ [FAKE] Payment URL created: {}", confirmUrl);
        return PaymentResult.success(confirmUrl, "FAKE-" + request.getPaymentRef());
    }

    @Override
    public PaymentVerifyResult verifyPayment(Map<String, String> params) {
        String paymentRef = params.get("paymentRef");
        log.info("🎭 [FAKE] Auto-verifying payment: ref={}", paymentRef);

        return PaymentVerifyResult.success(
                paymentRef,
                "FAKE-TXN-" + System.currentTimeMillis(),
                "{\"provider\":\"FAKE\",\"status\":\"SUCCESS\"}"
        );
    }
}
