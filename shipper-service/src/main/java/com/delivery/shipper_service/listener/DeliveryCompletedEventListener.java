package com.delivery.shipper_service.listener;

import com.delivery.shipper_service.common.constants.KafkaTopicConstants;
import com.delivery.shipper_service.dto.event.DeliveryCompletedEvent;
import com.delivery.shipper_service.service.ShipperBalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Kafka Listener để tự động cộng tiền vào shipper balance khi giao hàng hoàn thành
 */
@Slf4j
@Component
public class DeliveryCompletedEventListener {

    private final ShipperBalanceService shipperBalanceService;

    public DeliveryCompletedEventListener(ShipperBalanceService shipperBalanceService) {
        this.shipperBalanceService = shipperBalanceService;
    }

    @KafkaListener(topics = KafkaTopicConstants.DELIVERY_COMPLETED_TOPIC, groupId = "shipper-service-group")
    public void handleDeliveryCompleted(
            @Payload DeliveryCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            log.info("💰 Received DeliveryCompletedEvent for delivery: {}, shipper: {}, shippingFee: {}, shipperEarnings: {} from topic: {} partition: {} timestamp: {}",
                    event.getDeliveryId(), event.getShipperId(), event.getShippingFee(), event.getShipperEarnings(),
                    topic, partition, timestamp);

            // ✅ Validate event data
            if (event.getShipperId() == null) {
                log.error("💥 Invalid DeliveryCompletedEvent: shipperId is null");
                acknowledgment.acknowledge();
                return;
            }

            if (event.getShipperEarnings() == null || event.getShipperEarnings().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                log.error("💥 Invalid DeliveryCompletedEvent: shipperEarnings is null or <= 0");
                acknowledgment.acknowledge();
                return;
            }

            // ✅ Tự động cộng tiền vào balance của shipper (dùng shipperEarnings, không phải shippingFee)
            try {
                shipperBalanceService.earnFromOrderByUserId(
                        event.getShipperId(),
                        event.getOrderId(),
                        event.getShipperEarnings() // Shipper nhận 85% của shipping fee
                );

                log.info("✅ Successfully credited {} (85% of {}) to shipper {} balance for delivery {}",
                        event.getShipperEarnings(), event.getShippingFee(), event.getShipperId(), event.getDeliveryId());

            } catch (Exception e) {
                log.error("💥 Failed to credit shipper {} balance for delivery {}: {}",
                        event.getShipperId(), event.getDeliveryId(), e.getMessage(), e);
                // Không acknowledge để retry
                return;
            }

            // ✅ Acknowledge after successful processing
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Unexpected error processing DeliveryCompletedEvent for delivery: {} - Error: {}",
                    event.getDeliveryId(), e.getMessage(), e);

            // Acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
        }
    }
}
