package com.delivery.settlement_service.payment.provider;

import com.delivery.settlement_service.payment.PaymentProvider;
import com.delivery.settlement_service.payment.dto.PaymentRequest;
import com.delivery.settlement_service.payment.dto.PaymentResult;
import com.delivery.settlement_service.payment.dto.PaymentVerifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * VNPay Sandbox Payment Provider
 * Docs: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
 */
@Component
@Slf4j
public class VnPayProvider implements PaymentProvider {

    @Value("${payment.vnpay.tmn-code:DEMO_TMN_CODE}")
    private String tmnCode;

    @Value("${payment.vnpay.hash-secret:DEMO_HASH_SECRET}")
    private String hashSecret;

    @Value("${payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    private static final String VNP_VERSION = "2.1.0";
    private static final String VNP_COMMAND = "pay";
    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public String getProviderName() {
        return "VNPAY";
    }

    @Override
    public PaymentResult createPayment(PaymentRequest request) {
        try {
            log.info("🏦 [VNPay] Creating payment: ref={}, amount={}", request.getPaymentRef(), request.getAmount());

            Map<String, String> vnpParams = new TreeMap<>();
            vnpParams.put("vnp_Version", VNP_VERSION);
            vnpParams.put("vnp_Command", VNP_COMMAND);
            vnpParams.put("vnp_TmnCode", tmnCode);
            vnpParams.put("vnp_Amount", String.valueOf(request.getAmount().longValue() * 100)); // VNPay amount in VND * 100
            vnpParams.put("vnp_CurrCode", request.getCurrency() != null ? request.getCurrency() : "VND");
            vnpParams.put("vnp_TxnRef", request.getPaymentRef());
            vnpParams.put("vnp_OrderInfo", request.getOrderInfo() != null ? request.getOrderInfo() : "Nap tien ky quy");
            vnpParams.put("vnp_OrderType", "topup");
            vnpParams.put("vnp_Locale", request.getLocale() != null ? request.getLocale() : "vn");
            vnpParams.put("vnp_ReturnUrl", request.getReturnUrl());
            vnpParams.put("vnp_IpAddr", request.getIpAddress() != null ? request.getIpAddress() : "127.0.0.1");

            LocalDateTime now = LocalDateTime.now();
            vnpParams.put("vnp_CreateDate", now.format(VNP_DATE_FORMAT));
            vnpParams.put("vnp_ExpireDate", now.plusMinutes(15).format(VNP_DATE_FORMAT));

            // Build query string & sign
            String queryString = buildQueryString(vnpParams);
            String secureHash = hmacSHA512(hashSecret, queryString);
            String fullUrl = payUrl + "?" + queryString + "&vnp_SecureHash=" + secureHash;

            log.info("✅ [VNPay] Payment URL created for ref={}", request.getPaymentRef());
            return PaymentResult.success(fullUrl, request.getPaymentRef());

        } catch (Exception e) {
            log.error("❌ [VNPay] Failed to create payment: {}", e.getMessage(), e);
            return PaymentResult.failure("VNPay error: " + e.getMessage());
        }
    }

    @Override
    public PaymentVerifyResult verifyPayment(Map<String, String> params) {
        try {
            log.info("🔍 [VNPay] Verifying callback params...");

            String vnpSecureHash = params.get("vnp_SecureHash");
            if (vnpSecureHash == null) {
                return PaymentVerifyResult.invalidSignature("Missing vnp_SecureHash");
            }

            // Remove hash fields from params to verify
            Map<String, String> verifyParams = new TreeMap<>(params);
            verifyParams.remove("vnp_SecureHash");
            verifyParams.remove("vnp_SecureHashType");

            String queryString = buildQueryString(verifyParams);
            String calculatedHash = hmacSHA512(hashSecret, queryString);

            String rawPayload = params.toString();
            String paymentRef = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");
            String transactionNo = params.get("vnp_TransactionNo");

            if (!calculatedHash.equalsIgnoreCase(vnpSecureHash)) {
                log.warn("⚠️ [VNPay] Invalid signature for ref={}", paymentRef);
                return PaymentVerifyResult.invalidSignature("Checksum mismatch");
            }

            if ("00".equals(responseCode)) {
                log.info("✅ [VNPay] Payment SUCCESS for ref={}", paymentRef);
                return PaymentVerifyResult.success(paymentRef, transactionNo, rawPayload);
            } else {
                String message = getResponseMessage(responseCode);
                log.info("❌ [VNPay] Payment FAILED for ref={}, code={}, msg={}", paymentRef, responseCode, message);
                return PaymentVerifyResult.failed(paymentRef, responseCode, message, rawPayload);
            }

        } catch (Exception e) {
            log.error("❌ [VNPay] Verify error: {}", e.getMessage(), e);
            return PaymentVerifyResult.invalidSignature("Verify error: " + e.getMessage());
        }
    }

    // ═══════════════════════════════
    // Helper methods
    // ═══════════════════════════════

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA512", e);
        }
    }

    private String getResponseMessage(String code) {
        return switch (code) {
            case "07" -> "Trừ tiền thành công. Giao dịch bị nghi ngờ (liên quan tới lừa đảo, giao dịch bất thường).";
            case "09" -> "Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking tại ngân hàng.";
            case "10" -> "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần.";
            case "11" -> "Đã hết hạn chờ thanh toán.";
            case "12" -> "Thẻ/Tài khoản bị khóa.";
            case "13" -> "Sai mật khẩu xác thực giao dịch (OTP).";
            case "24" -> "Khách hàng hủy giao dịch.";
            case "51" -> "Tài khoản không đủ số dư.";
            case "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày.";
            case "75" -> "Ngân hàng thanh toán đang bảo trì.";
            case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định.";
            case "99" -> "Lỗi không xác định.";
            default -> "Lỗi thanh toán: " + code;
        };
    }
}
