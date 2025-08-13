package com.delivery.order_service.listener;

import com.delivery.order_service.common.constants.KafkaTopicConstants;
import com.delivery.order_service.dto.event.DeliveryStatusUpdatedEvent;
import com.delivery.order_service.service.OrderEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Delivery Event Listener theo AI Coding Instructions
 */
@Slf4j
@Component
public class DeliveryEventListener {

    private final OrderEventService orderEventService;

    // ✅ Constructor Injection (MANDATORY)
    public DeliveryEventListener(OrderEventService orderEventService) {
        this.orderEventService = orderEventService;
    }

    /**
     * ✅ Handle delivery status updates from Delivery Service
     */
    @KafkaListener(topics = KafkaTopicConstants.DELIVERY_STATUS_UPDATED_TOPIC)
    public void handleDeliveryStatusUpdated(
            @Payload DeliveryStatusUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received DeliveryStatusUpdatedEvent: orderId={}, deliveryId={}, status={}, previous={}",
                event.getOrderId(), event.getDeliveryId(), event.getStatus(), event.getPreviousStatus());

        try {
            // ✅ Validate event data
            if (event.getOrderId() == null) {
                log.error("💥 Invalid DeliveryStatusUpdatedEvent: orderId is null");
                return;
            }

            // ✅ Handle delivery status update using orderId to update order status
            orderEventService.handleDeliveryStatusUpdate(event);
            
            log.info("✅ Successfully processed DeliveryStatusUpdatedEvent for order: {} (delivery: {})", 
                    event.getOrderId(), event.getDeliveryId());

        } catch (Exception e) {
            log.error("💥 Failed to process DeliveryStatusUpdatedEvent for order {}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
