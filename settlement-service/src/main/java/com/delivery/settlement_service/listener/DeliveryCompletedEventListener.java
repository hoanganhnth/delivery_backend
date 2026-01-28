package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.common.constants.KafkaTopicConstants;
import com.delivery.settlement_service.dto.event.DeliveryCompletedEvent;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka listener to automatically create transactions when delivery is completed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCompletedEventListener {

    private final TransactionService transactionService;

    @KafkaListener(
            topics = KafkaTopicConstants.DELIVERY_COMPLETED_TOPIC,
            groupId = "settlement-service-group"
    )
    @Transactional
    public void handleDeliveryCompleted(
            @Payload DeliveryCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
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

                // 3. Create CREDIT transaction for shipper (DELIVERY_FEE)
                transactionService.createTransaction(
                        event.getShipperId(),
                        EntityType.SHIPPER,
                        event.getOrderId(),
                        TransactionDirection.CREDIT,
                        TransactionReason.DELIVERY_FEE,
                        event.getShipperEarnings(),
                        "Delivery fee from order #" + event.getOrderId() + " - Delivery completed"
                );

                log.info("✅ Credited {} to shipper {} for order {}",
                        event.getShipperEarnings(), event.getShipperId(), event.getOrderId());

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
