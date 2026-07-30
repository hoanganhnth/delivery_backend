package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.dto.event.DeliveryCompletedEvent;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.SettlementReceipt;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.WalletType;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.repository.SettlementReceiptRepository;
import com.delivery.settlement_service.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import com.delivery.settlement_service.metrics.BusinessMetrics;

/**
 * ✅ Kafka listener: Tạo giao dịch khi đơn hàng giao thành công
 * 
 * Mô hình 2 Ví (Dual Wallet):
 * - Shipper Ví Thu nhập (EARNINGS): Tiền công giao hàng
 * - Shipper Ví Ký quỹ (DEPOSIT):   Đối trừ tiền COD thu hộ
 * - Restaurant: Chỉ dùng 1 ví (EARNINGS)
 * 
 * Idempotent: stable event ID plus an immutable payload fingerprint.
 */
@Slf4j
@Component
public class DeliveryCompletedEventListener {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final SettlementReceiptRepository settlementReceiptRepository;
    private final BusinessMetrics businessMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    public DeliveryCompletedEventListener(TransactionService transactionService,
                                          TransactionRepository transactionRepository,
                                          SettlementReceiptRepository settlementReceiptRepository,
                                          BusinessMetrics businessMetrics) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.settlementReceiptRepository = settlementReceiptRepository;
        this.businessMetrics = businessMetrics;
    }

    // Retains the focused listener-test seam while production injection uses
    // the MeterRegistry-backed BusinessMetrics bean.
    DeliveryCompletedEventListener(TransactionService transactionService,
                                   TransactionRepository transactionRepository,
                                   SettlementReceiptRepository settlementReceiptRepository) {
        this(transactionService, transactionRepository, settlementReceiptRepository,
                new BusinessMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.retry.max-delay-ms:10000}"),
            exclude = IllegalArgumentException.class,
            kafkaTemplate = "retryKafkaTemplate",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            dltTopicSuffix = ".DLT")
    @KafkaListener(topics = "${app.kafka.topics.delivery-completed:delivery.completed}")
    @Transactional
    public void handleDeliveryCompleted(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        DeliveryCompletedEvent event = null;
        try {
            event = objectMapper.readValue(message, DeliveryCompletedEvent.class);
            log.info("💰 Received DeliveryCompletedEvent: delivery={}, order={}, restaurant={}, shipper={}, " +
                            "restaurantEarnings={}, shipperEarnings={}, paymentMethod={}",
                    event.getDeliveryId(), event.getOrderId(), event.getRestaurantId(), event.getShipperId(),
                    event.getRestaurantEarnings(), event.getShipperEarnings(), event.getPaymentMethod());

            // ── Validate ──────────────────────────────────────────
            if (event.getEventId() == null || !"DELIVERY_COMPLETED".equals(event.getEventType())
                    || !positive(event.getDeliveryId()) || !positive(event.getOrderId())
                    || !positive(event.getRestaurantId()) || !positive(event.getShipperId())) {
                throw new IllegalArgumentException("canonical event type and identity fields are required");
            }

            if (!"COD".equals(event.getPaymentMethod())) {
                throw new IllegalArgumentException("MVP settlement only accepts COD");
            }

            if (event.getRestaurantEarnings() == null || event.getRestaurantEarnings().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("restaurantEarnings is null or <= 0");
            }

            if (event.getShipperEarnings() == null || event.getShipperEarnings().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("shipperEarnings is null or <= 0");
            }

            if (!positive(event.getShippingFee())
                    || !nonNegative(event.getRestaurantCommission())
                    || !nonNegative(event.getShippingCommission())
                    || !positive(event.getTotalPlatformEarnings())) {
                throw new IllegalArgumentException("canonical fee/commission fields are missing or invalid");
            }

            BigDecimal calculatedPlatformEarnings = event.getRestaurantCommission()
                    .add(event.getShippingCommission());
            if (event.getTotalPlatformEarnings().compareTo(calculatedPlatformEarnings) != 0) {
                throw new IllegalArgumentException("totalPlatformEarnings does not match commissions");
            }

            // The delivery producer splits the canonical shipping fee into the
            // shipper's earnings and the platform's shipping commission. Without
            // this check a malformed event could keep the platform total correct
            // while crediting the shipper with an amount unrelated to the fee.
            BigDecimal calculatedShippingFee = event.getShipperEarnings()
                    .add(event.getShippingCommission());
            if (event.getShippingFee().compareTo(calculatedShippingFee) != 0) {
                throw new IllegalArgumentException("shippingFee does not match shipper earnings and commission");
            }

            if (registerReceiptOrIdentifyExactReplay(event)) {
                log.info("[Idempotent] Settlement event {} already applied, skipping", event.getEventId());
                acknowledgeAfterCommit(acknowledgment);
                return;
            }

            // Refuse to bless ledger data created before durable event receipts existed.
            if (transactionRepository.existsByOrderIdAndEntityIdAndEntityTypeAndReason(
                    event.getOrderId(), 0L, EntityType.SYSTEM, TransactionReason.PLATFORM_COMMISSION)) {
                throw new IllegalStateException("settlement ledger exists without a durable event receipt");
            }

            BigDecimal platformEarnings = calculatedPlatformEarnings;
            if (platformEarnings.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("total platform earnings must be greater than zero");
            }

            // ══════════════════════════════════════════════════
            // 1. RESTAURANT — credit the already-net earnings.
            // ══════════════════════════════════════════════════

            transactionService.createTransaction(
                    event.getRestaurantId(),
                    EntityType.RESTAURANT,
                    event.getOrderId(),
                    TransactionDirection.CREDIT,
                    TransactionReason.ORDER_EARNING,
                    event.getRestaurantEarnings(),
                    "Doanh thu đơn #" + event.getOrderId() + " (đã trừ hoa hồng)",
                    WalletType.EARNINGS
            );

            log.info("✅ Restaurant {} credited {} for order {}",
                    event.getRestaurantId(), event.getRestaurantEarnings(), event.getOrderId());

            // ══════════════════════════════════════════════════
            // 2. SHIPPER — delivery earnings.
            // ══════════════════════════════════════════════════

            transactionService.createTransaction(
                    event.getShipperId(),
                    EntityType.SHIPPER,
                    event.getOrderId(),
                    TransactionDirection.CREDIT,
                    TransactionReason.DELIVERY_FEE,
                    event.getShipperEarnings(),
                    "Tiền công giao đơn #" + event.getOrderId(),
                    WalletType.EARNINGS
            );

            log.info("✅ Shipper {} credited {} to Earnings for order {}",
                    event.getShipperId(), event.getShipperEarnings(), event.getOrderId());

            // ══════════════════════════════════════════════════
            // 3. COD — shipper collected the whole customer total in cash.
            // Debit that total from deposit exactly once, at completion.
            // ══════════════════════════════════════════════════

            BigDecimal totalCollected = event.getRestaurantEarnings()
                    .add(event.getRestaurantCommission())
                    .add(event.getShippingFee());

            transactionService.createTransaction(
                    event.getShipperId(),
                    EntityType.SHIPPER,
                    event.getOrderId(),
                    TransactionDirection.DEBIT,
                    TransactionReason.COD_SETTLEMENT,
                    totalCollected,
                    "Đối trừ COD đơn #" + event.getOrderId() + " (shipper đã thu " + totalCollected + " tiền mặt)",
                    WalletType.DEPOSIT
            );

            log.info("💵 Shipper {} COD settlement: -{} from Deposit for order {}",
                    event.getShipperId(), totalCollected, event.getOrderId());

            // ══════════════════════════════════════════════════
            // 4. PLATFORM — record commission as its own credit. The durable
            // receipt above owns replay identity; this remains the final ledger entry.
            // ══════════════════════════════════════════════════
            transactionService.createTransaction(
                    0L,
                    EntityType.SYSTEM,
                    event.getOrderId(),
                    TransactionDirection.CREDIT,
                    TransactionReason.PLATFORM_COMMISSION,
                    platformEarnings,
                    "Hoa hồng nền tảng đơn #" + event.getOrderId(),
                    WalletType.EARNINGS
            );

            // Acknowledge after successful processing
            acknowledgeAfterCommit(acknowledgment);
            businessMetrics.record("settlement_completed");
            log.info("✅ Successfully processed DeliveryCompletedEvent for delivery {}", event.getDeliveryId());

        } catch (IllegalArgumentException e) {
            log.error("💥 Invalid DeliveryCompletedEvent for delivery: {} - Error: {}",
                    event != null ? event.getDeliveryId() : "unknown", e.getMessage(), e);
            throw e;
        } catch (JsonProcessingException e) {
            log.error("💥 Invalid DeliveryCompletedEvent JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid delivery.completed JSON", e);
        } catch (Exception e) {
            log.error("💥 Settlement failed for delivery {}, record will be retried: {}",
                    event != null ? event.getDeliveryId() : "unknown", e.getMessage(), e);
            // Propagate so Spring rolls the database transaction back and Kafka
            // does not commit a partially posted financial event.
            throw new IllegalStateException("Failed to settle delivery", e);
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private boolean registerReceiptOrIdentifyExactReplay(DeliveryCompletedEvent event)
            throws JsonProcessingException {
        String fingerprint = fingerprint(event);
        SettlementReceipt byEvent = settlementReceiptRepository.findById(event.getEventId()).orElse(null);
        if (byEvent != null) {
            requireMatchingReceipt(byEvent, event, fingerprint);
            return true;
        }

        SettlementReceipt byOrder = settlementReceiptRepository.findByOrderId(event.getOrderId()).orElse(null);
        if (byOrder != null) {
            throw new IllegalArgumentException("order already settled by a different event: " + byOrder.getEventId());
        }

        settlementReceiptRepository.saveAndFlush(SettlementReceipt.builder()
                .eventId(event.getEventId())
                .orderId(event.getOrderId())
                .deliveryId(event.getDeliveryId())
                .payloadFingerprint(fingerprint)
                .createdAt(LocalDateTime.now())
                .build());
        return false;
    }

    private void requireMatchingReceipt(SettlementReceipt receipt, DeliveryCompletedEvent event, String fingerprint) {
        if (!receipt.getOrderId().equals(event.getOrderId())
                || !receipt.getDeliveryId().equals(event.getDeliveryId())
                || !receipt.getPayloadFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("eventId replay has a contradictory settlement payload");
        }
    }

    private String fingerprint(DeliveryCompletedEvent event) throws JsonProcessingException {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean nonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * Kafka offset acknowledgement must never get ahead of the financial DB
     * commit. Spring invokes this synchronization after the listener transaction
     * commits; direct unit tests without a transaction retain the old immediate
     * acknowledgement behavior.
     */
    private void acknowledgeAfterCommit(Acknowledgment acknowledgment) {
        if (acknowledgment == null) {
            throw new IllegalArgumentException("Kafka acknowledgment is required");
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            acknowledgment.acknowledge();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                acknowledgment.acknowledge();
            }
        });
    }
}
