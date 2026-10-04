package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.dto.event.DeliveryCompletedEvent;
import com.delivery.settlement.domain.ledger.CompletedCodDelivery;
import com.delivery.settlement.application.api.ledger.CodSettlementUseCase;
import com.delivery.settlement.application.ledger.DefaultCodSettlementUseCase;
import com.delivery.settlement_service.adapter.JpaCodSettlementAdapter;
import com.delivery.identity.contracts.SimulationContext;
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private final CodSettlementUseCase settlement;
    private final BusinessMetrics businessMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    public DeliveryCompletedEventListener(CodSettlementUseCase settlement, BusinessMetrics businessMetrics) {
        this.settlement = settlement;
        this.businessMetrics = businessMetrics;
    }

    // Focused adapter test seam; production composition lives in configuration.
    DeliveryCompletedEventListener(TransactionService transactions, TransactionRepository ledger,
            SettlementReceiptRepository receipts) {
        this(new DefaultCodSettlementUseCase(new JpaCodSettlementAdapter(transactions, ledger, receipts, null, "")),
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

            CompletedCodDelivery completion = completion(event);
            completion.requireCanonicalIdentity();

            // Simulation completions are acknowledged into the isolated
            // simulator ledger by the Control Plane; never touch real balance,
            // COD capacity or settlement receipts.
            SimulationContext context = SimulationContext.orReal(event.getSimulationContext());
            context.requireValid();
            if (context.isSimulation()) {
                log.info("Skipping real settlement for simulation run {} delivery {}",
                        context.runId(), event.getDeliveryId());
                acknowledgment.acknowledge();
                return;
            }

            // Validate before serializing the existing immutable receipt fingerprint.
            completion.plan();
            CodSettlementUseCase.Outcome outcome = settlement.settle(completion, fingerprint(event));
            acknowledgeAfterCommit(acknowledgment);
            if (outcome == CodSettlementUseCase.Outcome.POSTED) {
                businessMetrics.record("settlement_completed");
                log.info("Successfully settled delivery {}", event.getDeliveryId());
            } else {
                log.info("[Idempotent] Settlement event {} already applied, skipping", event.getEventId());
            }

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

    private CompletedCodDelivery completion(DeliveryCompletedEvent event) {
        return new CompletedCodDelivery(event.getEventId(), event.getEventType(), event.getDeliveryId(),
                event.getOrderId(), event.getRestaurantId(), event.getShipperId(), event.getPaymentMethod(),
                event.getRestaurantEarnings(), event.getShipperEarnings(), event.getRestaurantCommission(),
                event.getShippingCommission(), event.getTotalPlatformEarnings(), event.getShippingFee(),
                event.getGrossShippingFee(), event.getCustomerShippingFee(), event.getSubtotalPrice(),
                event.getShopDiscount(), event.getPlatformSubsidy(), event.getShippingDiscount(), event.getTotalPrice());
    }

    private String fingerprint(DeliveryCompletedEvent event) throws JsonProcessingException {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
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
