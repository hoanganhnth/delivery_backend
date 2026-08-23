package com.delivery.order_service.service;

import com.delivery.order_service.entity.OrderCreateIdempotencyReceipt;
import com.delivery.order_service.exception.OrderApiException;
import com.delivery.order_service.repository.OrderCreateIdempotencyReceiptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Database-backed create-order retry fence. */
@Service
public class OrderCreateIdempotencyService {
    private final OrderCreateIdempotencyReceiptRepository repository;

    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    private final Duration processingLease;

    @Autowired
    public OrderCreateIdempotencyService(OrderCreateIdempotencyReceiptRepository repository,
                                         @Value("${app.order.idempotency.processing-lease:PT30S}") Duration processingLease) {
        this.repository = repository;
        this.processingLease = boundedLease(processingLease);
    }

    /** Source-compatible constructor for focused unit tests and old callers. */
    public OrderCreateIdempotencyService(OrderCreateIdempotencyReceiptRepository repository) {
        this(repository, Duration.ofSeconds(30));
    }

    /**
     * Claims the key before remote preflight. The claim is leased so a crashed
     * request cannot permanently strand the idempotency key.
     */
    @Transactional
    public OrderCreateIdempotencyReceipt acquire(Long principalId, UUID key, String fingerprint,
                                                  UUID processingToken) {
        requireArguments(principalId, key, fingerprint, processingToken);
        Instant processingUntil = Instant.now().plus(processingLease);
        OrderCreateIdempotencyReceipt existing = repository
                .findByPrincipalIdAndIdempotencyKey(principalId, key).orElse(null);
        if (existing != null) {
            assertFingerprintMatches(existing, fingerprint);
            if (existing.getOrderId() != null) return existing;
            if (isOwnedAndLive(existing, processingToken)) return existing;
            if (isLive(existing)) {
                throw inProgress();
            }
        }

        int inserted = insertIfAbsentWithLease(principalId, key, fingerprint, processingToken, processingUntil);
        if (inserted == 1) {
            return repository.findByPrincipalIdAndIdempotencyKey(principalId, key).orElseThrow();
        }

        existing = repository.findByPrincipalIdAndIdempotencyKey(principalId, key).orElse(null);
        if (existing == null) throw inProgress();
        assertFingerprintMatches(existing, fingerprint);
        if (existing.getOrderId() != null) return existing;
        if (isOwnedAndLive(existing, processingToken)) return existing;
        if (claimExpiredLease(principalId, key, fingerprint, processingToken, processingUntil) == 1) {
            return repository.findByPrincipalIdAndIdempotencyKey(principalId, key).orElseThrow();
        }
        throw inProgress();
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

    /** Final transaction fence for a request that owns the preflight lease. */
    public OrderCreateIdempotencyReceipt claim(Long principalId, UUID key, String fingerprint,
                                               UUID processingToken) {
        requireArguments(principalId, key, fingerprint, processingToken);
        OrderCreateIdempotencyReceipt existing = repository.findByPrincipalIdAndIdempotencyKeyForUpdate(principalId, key)
                .orElseThrow(() -> inProgress());
        assertFingerprintMatches(existing, fingerprint);
        if (existing.getOrderId() != null) return existing;
        if (!processingToken.equals(existing.getProcessingToken()) || !isLive(existing)) {
            throw inProgress();
        }
        return existing;
    }

    /**
     * Read-only fast path for a completed replay.  The actual claim still runs
     * inside the final order transaction, so this method is only an optimisation
     * and never the correctness boundary.
     */
    public Optional<OrderCreateIdempotencyReceipt> findExisting(Long principalId, UUID key) {
        if (principalId == null || key == null) {
            return Optional.empty();
        }
        return repository.findByPrincipalIdAndIdempotencyKey(principalId, key);
    }

    public void assertFingerprintMatches(OrderCreateIdempotencyReceipt receipt, String fingerprint) {
        if (receipt == null || fingerprint == null) {
            throw new IllegalArgumentException("Idempotency receipt and fingerprint are required");
        }
        if (!fingerprint.equals(receipt.getRequestFingerprint())
                || !CheckoutFingerprintService.VERSION.equals(receipt.getFingerprintVersion())) {
            throw new OrderApiException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key đã được dùng với dữ liệu khác");
        }
    }

    public void complete(OrderCreateIdempotencyReceipt receipt, Long orderId) {
        receipt.complete(orderId);
    }

    @Transactional
    public void release(Long receiptId, UUID processingToken) {
        if (receiptId != null && processingToken != null) {
            repository.releaseLease(receiptId, processingToken);
        }
    }

    private int insertIfAbsent(Long principalId, UUID key, String fingerprint) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return repository.insertIfAbsentH2(principalId, key, fingerprint, CheckoutFingerprintService.VERSION);
        }
        return repository.insertIfAbsentPostgres(principalId, key, fingerprint, CheckoutFingerprintService.VERSION);
    }

    private int insertIfAbsentWithLease(Long principalId, UUID key, String fingerprint,
                                        UUID processingToken, Instant processingUntil) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return repository.insertIfAbsentWithLeaseH2(principalId, key, fingerprint,
                    CheckoutFingerprintService.VERSION, processingToken, processingUntil);
        }
        return repository.insertIfAbsentWithLeasePostgres(principalId, key, fingerprint,
                CheckoutFingerprintService.VERSION, processingToken, processingUntil);
    }

    private int claimExpiredLease(Long principalId, UUID key, String fingerprint,
                                  UUID processingToken, Instant processingUntil) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return repository.claimExpiredLeaseH2(principalId, key, fingerprint,
                    CheckoutFingerprintService.VERSION, processingToken, processingUntil);
        }
        return repository.claimExpiredLeasePostgres(principalId, key, fingerprint,
                CheckoutFingerprintService.VERSION, processingToken, processingUntil);
    }

    private void requireArguments(Long principalId, UUID key, String fingerprint, UUID processingToken) {
        if (principalId == null || key == null || fingerprint == null || processingToken == null) {
            throw new IllegalArgumentException("Idempotency claim arguments are required");
        }
    }

    private boolean isOwnedAndLive(OrderCreateIdempotencyReceipt receipt, UUID token) {
        return token.equals(receipt.getProcessingToken()) && isLive(receipt);
    }

    private boolean isLive(OrderCreateIdempotencyReceipt receipt) {
        return receipt.getProcessingUntil() != null && receipt.getProcessingUntil().isAfter(Instant.now());
    }

    private OrderApiException inProgress() {
        return new OrderApiException("IDEMPOTENCY_IN_PROGRESS",
                "Yêu cầu đặt đơn đang được xử lý, vui lòng thử lại với cùng Idempotency-Key");
    }

    private Duration boundedLease(Duration value) {
        Duration candidate = value == null ? Duration.ofSeconds(30) : value;
        return candidate.compareTo(Duration.ofSeconds(5)) < 0 ? Duration.ofSeconds(5)
                : candidate.compareTo(Duration.ofMinutes(5)) > 0 ? Duration.ofMinutes(5) : candidate;
    }
}
