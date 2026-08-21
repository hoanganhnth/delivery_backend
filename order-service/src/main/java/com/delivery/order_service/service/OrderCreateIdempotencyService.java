package com.delivery.order_service.service;

import com.delivery.order_service.entity.OrderCreateIdempotencyReceipt;
import com.delivery.order_service.exception.OrderApiException;
import com.delivery.order_service.repository.OrderCreateIdempotencyReceiptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Database-backed create-order retry fence. */
@Service
public class OrderCreateIdempotencyService {
    private final OrderCreateIdempotencyReceiptRepository repository;

    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    public OrderCreateIdempotencyService(OrderCreateIdempotencyReceiptRepository repository) {
        this.repository = repository;
    }

    public OrderCreateIdempotencyReceipt claim(Long principalId, UUID key, String fingerprint) {
        OrderCreateIdempotencyReceipt existing = repository.findByPrincipalIdAndIdempotencyKey(principalId, key)
                .orElse(null);
        boolean insertedByThisClaim = false;
        if (existing == null && insertIfAbsent(principalId, key, fingerprint) == 1) {
            insertedByThisClaim = true;
            existing = repository.findByPrincipalIdAndIdempotencyKey(principalId, key).orElseThrow();
        }
        if (existing == null) {
            existing = repository.findByPrincipalIdAndIdempotencyKey(principalId, key).orElseThrow(() ->
                    new OrderApiException("IDEMPOTENCY_IN_PROGRESS", "Yêu cầu đặt đơn đang được xử lý"));
        }
        if (!existing.getRequestFingerprint().equals(fingerprint)
                || !CheckoutFingerprintService.VERSION.equals(existing.getFingerprintVersion())) {
            throw new OrderApiException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key đã được dùng với dữ liệu khác");
        }
        if (!insertedByThisClaim && existing.getOrderId() == null) {
            throw new OrderApiException("IDEMPOTENCY_IN_PROGRESS",
                    "Yêu cầu đặt đơn đang được xử lý, vui lòng thử lại với cùng Idempotency-Key");
        }
        return existing;
    }

    public void complete(OrderCreateIdempotencyReceipt receipt, Long orderId) {
        receipt.complete(orderId);
    }

    private int insertIfAbsent(Long principalId, UUID key, String fingerprint) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return repository.insertIfAbsentH2(principalId, key, fingerprint, CheckoutFingerprintService.VERSION);
        }
        return repository.insertIfAbsentPostgres(principalId, key, fingerprint, CheckoutFingerprintService.VERSION);
    }
}
