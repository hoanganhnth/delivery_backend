package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.PaymentOrderResponse;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlement/payments")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {"app.payment.processing-enabled", "app.payment.fake-provider-enabled"},
        havingValue = "true")
@Profile({"dev", "test"})
public class FakePaymentController {

    private final PaymentService paymentService;

    @GetMapping("/fake-confirm/{paymentRef}")
    public ResponseEntity<BaseResponse<PaymentOrderResponse>> fakeConfirm(
            @PathVariable String paymentRef) {
        PaymentOrderResponse response = paymentService.confirmFakePayment(paymentRef);
        return ResponseEntity.ok(BaseResponse.success(response, "Fake payment confirmed"));
    }
}
