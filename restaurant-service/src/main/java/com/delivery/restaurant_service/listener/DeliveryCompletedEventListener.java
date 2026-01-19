package com.delivery.restaurant_service.listener;

import com.delivery.restaurant_service.common.constants.KafkaTopicConstants;
import com.delivery.restaurant_service.dto.event.DeliveryCompletedEvent;
import com.delivery.restaurant_service.service.RestaurantBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Listener to automatically credit restaurant balance when delivery is
 * completed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCompletedEventListener {

    private final RestaurantBalanceService restaurantBalanceService;

    @KafkaListener(topics = KafkaTopicConstants.DELIVERY_COMPLETED_TOPIC, groupId = "restaurant-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleDeliveryCompleted(
            @Payload DeliveryCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            log.info(
                    "💰 Received DeliveryCompletedEvent for delivery: {}, restaurant: {}, restaurantEarnings: {} from topic: {} partition: {} timestamp: {}",
                    event.getDeliveryId(), event.getRestaurantId(), event.getRestaurantEarnings(),
                    topic, partition, timestamp);

            // Validate event data
            if (event.getRestaurantId() == null) {
                log.error("💥 Invalid DeliveryCompletedEvent: restaurantId is null");
                acknowledgment.acknowledge();
                return;
            }

            if (event.getRestaurantEarnings() == null
                    || event.getRestaurantEarnings().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                log.error("💥 Invalid DeliveryCompletedEvent: restaurantEarnings is null or <= 0");
                acknowledgment.acknowledge();
                return;
            }

            // Automatically credit restaurant balance
            try {
                restaurantBalanceService.earnFromOrder(
                        event.getRestaurantId(),
                        event.getOrderId(),
                        event.getRestaurantEarnings(),
                        "Earnings from order #" + event.getOrderId() + " - Delivery completed");

                log.info("✅ Successfully credited {} to restaurant {} balance for delivery {}",
                        event.getRestaurantEarnings(), event.getRestaurantId(), event.getDeliveryId());

            } catch (Exception e) {
                log.error("💥 Failed to credit restaurant {} balance for delivery {}: {}",
                        event.getRestaurantId(), event.getDeliveryId(), e.getMessage(), e);
                // Don't acknowledge to retry
                return;
            }

            // Acknowledge after successful processing
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Unexpected error processing DeliveryCompletedEvent for delivery: {} - Error: {}",
                    event.getDeliveryId(), e.getMessage(), e);

            // Acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
        }
    }
}
