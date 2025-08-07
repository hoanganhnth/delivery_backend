package com.delivery.order_service.listener;

import com.delivery.order_service.common.constants.KafkaTopicConstants;
import com.delivery.order_service.dto.event.ShipperEvent;
import com.delivery.order_service.service.OrderEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Shipper Event Listener theo AI Coding Instructions
 */
@Slf4j
@Component
public class ShipperEventListener {

    private final OrderEventService orderEventService;

    // ✅ Constructor Injection (MANDATORY)
    public ShipperEventListener(OrderEventService orderEventService) {
        this.orderEventService = orderEventService;
    }

    /**
     * ✅ Handle shipper accepted events
     */
    @KafkaListener(topics = KafkaTopicConstants.SHIPPER_ACCEPTED_TOPIC)
    public void handleShipperAccepted(
            @Payload ShipperEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received ShipperAcceptedEvent: orderId={}, deliveryId={}, shipperId={}",
                event.getOrderId(), event.getDeliveryId(), event.getShipperId());

        try {
            orderEventService.handleShipperAccepted(event);
            
            log.info("✅ Successfully processed ShipperAcceptedEvent for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("💥 Failed to process ShipperAcceptedEvent for order {}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * ✅ Handle shipper rejected events
     */
    @KafkaListener(topics = KafkaTopicConstants.SHIPPER_REJECTED_TOPIC)
    public void handleShipperRejected(
            @Payload ShipperEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received ShipperRejectedEvent: orderId={}, deliveryId={}, shipperId={}, reason={}",
                event.getOrderId(), event.getDeliveryId(), event.getShipperId(), event.getRejectReason());

        try {
            orderEventService.handleShipperRejected(event);
            
            log.info("✅ Successfully processed ShipperRejectedEvent for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("💥 Failed to process ShipperRejectedEvent for order {}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
