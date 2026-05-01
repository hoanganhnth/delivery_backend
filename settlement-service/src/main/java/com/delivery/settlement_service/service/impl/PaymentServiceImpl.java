package com.delivery.settlement_service.service.impl;

import com.delivery.settlement_service.dto.request.CreatePaymentRequest;
import com.delivery.settlement_service.dto.response.PaymentOrderResponse;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.PaymentOrder;
import com.delivery.settlement_service.entity.PaymentOrder.PaymentPurpose;
import com.delivery.settlement_service.entity.PaymentOrder.PaymentStatus;
import com.delivery.settlement_service.payment.PaymentProvider;
import com.delivery.settlement_service.payment.PaymentProviderRegistry;
import com.delivery.settlement_service.payment.dto.PaymentRequest;
import com.delivery.settlement_service.payment.dto.PaymentResult;
import com.delivery.settlement_service.payment.dto.PaymentVerifyResult;
import com.delivery.settlement_service.repository.PaymentOrderRepository;
import com.delivery.settlement_service.service.PaymentService;
import com.delivery.settlement_service.service.TransactionService;
import com.delivery.settlement_service.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final TransactionService transactionService;
    private final com.delivery.settlement_service.service.PaymentEventPublisher paymentEventPublisher;

    @Value("${payment.default-provider:FAKE}")
    private String defaultProvider;

    @Value("${payment.order-expiry-minutes:15}")
    private int orderExpiryMinutes;

    @Value("${payment.vnpay.return-url:http://localhost:8095/api/settlement/payments/vnpay-callback}")
    private String defaultReturnUrl;

    // ═══════════════════════════════════════════════════════════════
    // CREATE PAYMENT
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public PaymentOrderResponse createPayment(CreatePaymentRequest request) {
        String providerName = request.getProvider() != null ? request.getProvider() : defaultProvider;
        log.info("💳 Creating payment: entityId={}, amount={}, provider={}", 
                request.getEntityId(), request.getAmount(), providerName);

        // 1. Validate provider
        PaymentProvider provider = providerRegistry.getProvider(providerName);

        // 2. Parse enums
        EntityType entityType = parseEntityType(request.getEntityType());
        PaymentPurpose purpose = parsePurpose(request.getPurpose());

        // 3. Generate unique ref
        String paymentRef = generatePaymentRef();

        // 4. Create PaymentOrder entity (PENDING)
        PaymentOrder order = PaymentOrder.builder()
                .paymentRef(paymentRef)
                .entityId(request.getEntityId())
                .entityType(entityType)
                .orderId(request.getOrderId())
                .provider(providerName.toUpperCase())
                .amount(request.getAmount())
                .purpose(purpose)
                .status(PaymentStatus.PENDING)
                .returnUrl(request.getReturnUrl())
                .ipAddress(request.getIpAddress())
                .expiredAt(LocalDateTime.now().plusMinutes(orderExpiryMinutes))
                .build();

        paymentOrderRepository.save(order);

        // 5. Call provider to create payment URL
        String returnUrl = request.getReturnUrl() != null ? request.getReturnUrl() : defaultReturnUrl;
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .paymentRef(paymentRef)
                .amount(request.getAmount())
                .currency("VND")
                .orderInfo("Nap tien ky quy - " + paymentRef)
                .returnUrl(returnUrl)
                .ipAddress(request.getIpAddress() != null ? request.getIpAddress() : "127.0.0.1")
                .locale("vn")
                .build();

        PaymentResult result = provider.createPayment(paymentRequest);

        if (!result.isSuccess()) {
            order.setStatus(PaymentStatus.FAILED);
            order.setCallbackPayload(result.getErrorMessage());
            paymentOrderRepository.save(order);
            throw new RuntimeException("Payment creation failed: " + result.getErrorMessage());
        }

        // 6. Update order with payment URL
        order.setPaymentUrl(result.getPaymentUrl());
        order.setProviderTransactionId(result.getProviderTransactionId());
        paymentOrderRepository.save(order);

        log.info("✅ Payment created: ref={}, url={}", paymentRef, result.getPaymentUrl());
        return toResponse(order);
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLE CALLBACK (VNPay return / IPN)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public PaymentOrderResponse handleCallback(String providerName, Map<String, String> params) {
        log.info("📨 Handling callback from provider: {}", providerName);

        PaymentProvider provider = providerRegistry.getProvider(providerName);
        PaymentVerifyResult verifyResult = provider.verifyPayment(params);

        if (!verifyResult.isVerified()) {
            log.warn("⚠️ Invalid signature from {}: {}", providerName, verifyResult.getMessage());
            throw new SecurityException("Invalid payment callback signature");
        }

        // Find payment order
        PaymentOrder order = paymentOrderRepository.findByPaymentRef(verifyResult.getPaymentRef())
                .orElseThrow(() -> new RuntimeException(
                        "Payment order not found: " + verifyResult.getPaymentRef()));

        // Idempotency check — already processed
        if (order.getStatus() != PaymentStatus.PENDING) {
            log.info("⏭️ Payment already processed: ref={}, status={}", order.getPaymentRef(), order.getStatus());
            return toResponse(order);
        }

        // 🛡️ Security Check: Validate amount matches
        if (verifyResult.getAmount() != null) {
            long expectedAmount = order.getAmount().longValue() * 100;
            if (verifyResult.getAmount() != expectedAmount) {
                log.error("🚨 Amount mismatch! ref={}, expected={}, received={}", 
                        order.getPaymentRef(), expectedAmount, verifyResult.getAmount());
                order.setStatus(PaymentStatus.FAILED);
                order.setCallbackPayload("Amount mismatch: " + verifyResult.getAmount());
                paymentOrderRepository.save(order);
                throw new SecurityException("Payment amount mismatch");
            }
        }

        // Update order with callback data
        order.setCallbackPayload(verifyResult.getRawPayload());
        order.setProviderTransactionId(verifyResult.getProviderTransactionId());

        if (verifyResult.isPaymentSuccess()) {
            return processSuccessfulPayment(order);
        } else {
            order.setStatus(PaymentStatus.FAILED);
            paymentOrderRepository.save(order);
            log.info("❌ Payment failed: ref={}, code={}", order.getPaymentRef(), verifyResult.getResponseCode());
            
            // Notify other services
            paymentEventPublisher.publishPaymentFailed(order, verifyResult.getMessage());
            
            return toResponse(order);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FAKE CONFIRM (dev/test)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public PaymentOrderResponse confirmFakePayment(String paymentRef) {
        log.info("🎭 Confirming fake payment: ref={}", paymentRef);

        PaymentOrder order = paymentOrderRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new RuntimeException("Payment order not found: " + paymentRef));

        if (!"FAKE".equalsIgnoreCase(order.getProvider())) {
            throw new IllegalArgumentException("Only FAKE payments can be confirmed via this endpoint");
        }

        if (order.getStatus() != PaymentStatus.PENDING) {
            log.info("⏭️ Payment already processed: ref={}, status={}", paymentRef, order.getStatus());
            return toResponse(order);
        }

        order.setCallbackPayload("{\"provider\":\"FAKE\",\"status\":\"SUCCESS\"}");
        return processSuccessfulPayment(order);
    }

    // ═══════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse getPaymentStatus(Long paymentId) {
        PaymentOrder order = paymentOrderRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment order not found: " + paymentId));
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse getPaymentByRef(String paymentRef) {
        PaymentOrder order = paymentOrderRepository.findByPaymentRef(paymentRef)
                .orElseThrow(() -> new RuntimeException("Payment order not found: " + paymentRef));
        return toResponse(order);
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Xử lý thanh toán thành công → tạo transaction trong settlement
     */
    private PaymentOrderResponse processSuccessfulPayment(PaymentOrder order) {
        order.setStatus(PaymentStatus.SUCCESS);

        // 1. Xử lý logic nghiệp vụ theo mục đích
        if (order.getPurpose() == PaymentPurpose.DEPOSIT_TOPUP) {
            // Nạp tiền ký quỹ cho Shipper/Restaurant
            Transaction tx = transactionService.topUpDeposit(
                    order.getEntityId(),
                    order.getAmount(),
                    order.getProvider()
            );
            order.setSettlementTransactionId(tx.getId());
            log.info("✅ Payment SUCCESS → Deposit topped up: ref={}, txId={}", 
                    order.getPaymentRef(), tx.getId());
        } else if (order.getPurpose() == PaymentPurpose.ORDER_PAYMENT) {
            // Thanh toán đơn hàng — lúc này entityId chính là orderId
            log.info("✅ Payment SUCCESS for Order ID: {}", order.getEntityId());
            // Logic cộng tiền cho hệ thống hoặc trung gian nếu cần
        }

        // 2. Notify other services via Kafka (ví dụ: order-service)
        paymentEventPublisher.publishPaymentSuccess(order);

        paymentOrderRepository.save(order);
        return toResponse(order);
    }

    private String generatePaymentRef() {
        return "PAY-" + System.currentTimeMillis() + "-" 
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private EntityType parseEntityType(String type) {
        try {
            return type != null ? EntityType.valueOf(type.toUpperCase()) : EntityType.SHIPPER;
        } catch (IllegalArgumentException e) {
            return EntityType.SHIPPER;
        }
    }

    private PaymentPurpose parsePurpose(String purpose) {
        try {
            return purpose != null ? PaymentPurpose.valueOf(purpose.toUpperCase()) : PaymentPurpose.DEPOSIT_TOPUP;
        } catch (IllegalArgumentException e) {
            return PaymentPurpose.DEPOSIT_TOPUP;
        }
    }

    private PaymentOrderResponse toResponse(PaymentOrder order) {
        return PaymentOrderResponse.builder()
                .id(order.getId())
                .paymentRef(order.getPaymentRef())
                .entityId(order.getEntityId())
                .entityType(order.getEntityType().name())
                .provider(order.getProvider())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .purpose(order.getPurpose().name())
                .status(order.getStatus().name())
                .paymentUrl(order.getPaymentUrl())
                .providerTransactionId(order.getProviderTransactionId())
                .settlementTransactionId(order.getSettlementTransactionId())
                .createdAt(order.getCreatedAt())
                .expiredAt(order.getExpiredAt())
                .build();
    }
}
