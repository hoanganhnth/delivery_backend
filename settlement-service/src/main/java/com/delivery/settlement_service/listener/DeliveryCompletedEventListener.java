package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.common.constants.KafkaTopicConstants;
import com.delivery.settlement_service.dto.event.DeliveryCompletedEvent;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka listener to automatically create transactions when delivery is completed
 * ✅ Idempotent — kiểm tra orderId trước khi cộng tiền, tránh duplicate khi Kafka retry
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCompletedEventListener {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @KafkaListener(
            topics = KafkaTopicConstants.DELIVERY_COMPLETED_TOPIC,
            groupId = "settlement-service-group"
    )
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
                            "restaurantEarnings={}, shipperEarnings={}, commission={} from topic={} partition={} timestamp={}",
                    event.getDeliveryId(), event.getOrderId(), event.getRestaurantId(), event.getShipperId(),
                    event.getRestaurantEarnings(), event.getShipperEarnings(), event.getPlatformCommission(),
                    topic, partition, timestamp);

            // Validate event data
            if (event.getRestaurantId() == null) {
                log.error("💥 Invalid DeliveryCompletedEvent: restaurantId is null");
                acknowledgment.acknowledge();
                return;
            }

            if (event.getShipperId() == null) {
                log.error("💥 Invalid DeliveryCompletedEvent: shipperId is null");
                acknowledgment.acknowledge();
                return;
            }

            if (event.getRestaurantEarnings() == null || event.getRestaurantEarnings().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                log.error("💥 Invalid DeliveryCompletedEvent: restaurantEarnings is null or <= 0");
                acknowledgment.acknowledge();
                return;
            }

            if (event.getShipperEarnings() == null || event.getShipperEarnings().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                log.error("💥 Invalid DeliveryCompletedEvent: shipperEarnings is null or <= 0");
                acknowledgment.acknowledge();
                return;
            }

            try {
                // ✅ IDEMPOTENCY CHECK: Restaurant đã được cộng tiền cho order này chưa?
                if (transactionRepository.existsByOrderIdAndEntityIdAndEntityTypeAndReason(
                        event.getOrderId(), event.getRestaurantId(),
                        EntityType.RESTAURANT, TransactionReason.ORDER_EARNING)) {
                    log.warn("⚠️ [Idempotent] Restaurant {} already credited for order {}, skipping",
                            event.getRestaurantId(), event.getOrderId());
                    acknowledgment.acknowledge();
                    return;
                }

                boolean isCOD = "COD".equalsIgnoreCase(event.getPaymentMethod());

                // ✅ IDEMPOTENCY CHECK: Shipper đã được cộng tiền cho order này chưa? (Chỉ check cho pre-paid)
                if (!isCOD && transactionRepository.existsByOrderIdAndEntityIdAndEntityTypeAndReason(
                        event.getOrderId(), event.getShipperId(),
                        EntityType.SHIPPER, TransactionReason.DELIVERY_FEE)) {
                    log.warn("⚠️ [Idempotent] Shipper {} already credited for order {}, skipping",
                            event.getShipperId(), event.getOrderId());
                    acknowledgment.acknowledge();
                    return;
                }

                // 1. Create CREDIT transaction for restaurant (ORDER_EARNING)
                transactionService.createTransaction(
                        event.getRestaurantId(),
                        EntityType.RESTAURANT,
                        event.getOrderId(),
                        TransactionDirection.CREDIT,
                        TransactionReason.ORDER_EARNING,
                        event.getRestaurantEarnings(),
                        "Earnings from order #" + event.getOrderId() + " - Delivery completed"
                );

                log.info("✅ Credited {} to restaurant {} for order {}",
                        event.getRestaurantEarnings(), event.getRestaurantId(), event.getOrderId());

                // 2. Create DEBIT transaction for platform commission (optional - track platform revenue)
                if (event.getPlatformCommission() != null && event.getPlatformCommission().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    transactionService.createTransaction(
                            event.getRestaurantId(),
                            EntityType.RESTAURANT,
                            event.getOrderId(),
                            TransactionDirection.DEBIT,
                            TransactionReason.PLATFORM_COMMISSION,
                            event.getPlatformCommission(),
                            "Platform commission for order #" + event.getOrderId()
                    );

                    log.info("✅ Recorded platform commission {} for order {}",
                            event.getPlatformCommission(), event.getOrderId());
                }

                // 3. Create CREDIT transaction for shipper (DELIVERY_FEE) — CHỈ cho đơn Pre-paid
                // COD: Shipper đã cầm tiền mặt, không cần CREDIT thêm vào ví
                if (!isCOD) {
                    transactionService.createTransaction(
                            event.getShipperId(),
                            EntityType.SHIPPER,
                            event.getOrderId(),
                            TransactionDirection.CREDIT,
                            TransactionReason.DELIVERY_FEE,
                            event.getShipperEarnings(),
                            "Delivery fee from order #" + event.getOrderId() + " - Delivery completed"
                    );

                    log.info("✅ Credited {} to shipper {} for order {} (Pre-paid)",
                            event.getShipperEarnings(), event.getShipperId(), event.getOrderId());
                } else {
                    log.info("💵 Skipping shipper CREDIT for COD order {} — shipper holds {} cash",
                            event.getOrderId(), event.getShipperEarnings());
                }

            } catch (Exception e) {
                log.error("💥 Failed to create transactions for delivery {}: {}",
                        event.getDeliveryId(), e.getMessage(), e);
                // Don't acknowledge to retry
                return;
            }

            // Acknowledge after successful processing
            acknowledgment.acknowledge();
            log.info("✅ Successfully processed DeliveryCompletedEvent for delivery {}", event.getDeliveryId());

        } catch (Exception e) {
            log.error("💥 Unexpected error processing DeliveryCompletedEvent for delivery: {} - Error: {}",
                    event.getDeliveryId(), e.getMessage(), e);

            // Acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
        }
    }
}
