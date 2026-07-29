package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.request.CreatePaymentRequest;
import com.delivery.settlement_service.dto.response.PaymentOrderResponse;
import com.delivery.settlement_service.payment.PaymentProviderRegistry;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/settlement/payments")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.payment.processing-enabled", havingValue = "true")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProviderRegistry providerRegistry;

    // ═══════════════════════════════════════════════════════════════
    // CREATE PAYMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tạo giao dịch thanh toán mới
     * Client nhận paymentUrl để redirect đến cổng thanh toán
     */
    @PostMapping("/create")
    public ResponseEntity<BaseResponse<PaymentOrderResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {

        // Auto-detect IP nếu client không truyền
        if (request.getIpAddress() == null || request.getIpAddress().isBlank()) {
            request.setIpAddress(getClientIp(httpRequest));
        }

        PaymentOrderResponse response = paymentService.createPayment(request);
        return ResponseEntity.ok(BaseResponse.success(response, "Payment created"));
    }

    // ═══════════════════════════════════════════════════════════════
    // VNPAY CALLBACK (Return URL)
    // ═══════════════════════════════════════════════════════════════

    /**
     * VNPay redirect callback — user được redirect về đây sau khi thanh toán
     */
    @GetMapping("/vnpay-callback")
    public ResponseEntity<BaseResponse<PaymentOrderResponse>> vnpayCallback(
            @RequestParam Map<String, String> params) {

        log.info("📨 VNPay callback received: {}", params.get("vnp_TxnRef"));
        PaymentOrderResponse response = paymentService.handleCallback("VNPAY", params);
        String message = "SUCCESS".equals(response.getStatus())
                ? "Thanh toán thành công"
                : "Thanh toán thất bại";
        return ResponseEntity.ok(BaseResponse.success(response, message));
    }

    // ═══════════════════════════════════════════════════════════════
    // VNPAY IPN (Server-to-Server)
    // ═══════════════════════════════════════════════════════════════

    /**
     * VNPay IPN webhook — VNPay server gọi trực tiếp (backup confirmation)
     * VNPay gọi IPN qua phương thức GET.
     */
    @RequestMapping(value = "/vnpay-ipn", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        log.info("📨 VNPay IPN received: ref={}, code={}", params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"));

        Map<String, String> ipnResponse = new HashMap<>();
        try {
            paymentService.handleCallback("VNPAY", params);
            ipnResponse.put("RspCode", "00");
            ipnResponse.put("Message", "Confirm Success");
        } catch (SecurityException e) {
            ipnResponse.put("RspCode", "97");
            ipnResponse.put("Message", "Invalid Checksum");
        } catch (Exception e) {
            ipnResponse.put("RspCode", "99");
            ipnResponse.put("Message", e.getMessage());
        }

        return ResponseEntity.ok(ipnResponse);
    }

    // ═══════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Query trạng thái giao dịch theo ID
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<BaseResponse<PaymentOrderResponse>> getPaymentStatus(
            @PathVariable Long paymentId) {

        PaymentOrderResponse response = paymentService.getPaymentStatus(paymentId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    /**
     * Query trạng thái giao dịch theo mã tham chiếu
     */
    @GetMapping("/ref/{paymentRef}")
    public ResponseEntity<BaseResponse<PaymentOrderResponse>> getPaymentByRef(
            @PathVariable String paymentRef) {

        PaymentOrderResponse response = paymentService.getPaymentByRef(paymentRef);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    /**
     * Danh sách provider khả dụng
     */
    @GetMapping("/providers")
    public ResponseEntity<BaseResponse<Set<String>>> getAvailableProviders() {
        Set<String> providers = providerRegistry.getAvailableProviders();
        return ResponseEntity.ok(BaseResponse.success(providers, "Available providers"));
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════════

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Lấy IP đầu tiên nếu có nhiều (proxy chain)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "127.0.0.1";
    }
}
