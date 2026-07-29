package com.delivery.order_service.listener;

import com.delivery.order_service.common.constants.KafkaTopicConstants;
import com.delivery.order_service.dto.event.PaymentEvent;
import com.delivery.order_service.service.OrderEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * ✅ Payment Event Listener theo AI Coding Instructions
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.order.payment-event-processing-enabled", havingValue = "true")
public class PaymentEventListener {

    private final OrderEventService orderEventService;

    // ✅ Constructor Injection (MANDATORY)
    public PaymentEventListener(OrderEventService orderEventService) {
        this.orderEventService = orderEventService;
    }

    /**
     * ✅ Handle payment completed events
     */
    @KafkaListener(topics = KafkaTopicConstants.PAYMENT_COMPLETED_TOPIC)
    public void handlePaymentCompleted(
            @Payload PaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset,
            Acknowledgment acknowledgment) {

        log.info("📥 Received PaymentCompletedEvent: orderId={}, paymentId={}, amount={}",
                event.getOrderId(), event.getPaymentId(), event.getAmount());

        try {
            orderEventService.handlePaymentCompleted(event);
            acknowledgment.acknowledge();
            log.info("✅ Successfully processed PaymentCompletedEvent for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("💥 Failed to process PaymentCompletedEvent for order {}: {}", 
                    event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * ✅ Handle payment failed events
     */
    @KafkaListener(topics = KafkaTopicConstants.PAYMENT_FAILED_TOPIC)
    public void handlePaymentFailed(
            @Payload PaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset,
            Acknowledgment acknowledgment) {

        log.info("📥 Received PaymentFailedEvent: orderId={}, paymentId={}, reason={}",
                event.getOrderId(), event.getPaymentId(), event.getFailureReason());

        try {
            orderEventService.handlePaymentFailed(event);
            acknowledgment.acknowledge();
            log.info("✅ Successfully processed PaymentFailedEvent for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("💥 Failed to process PaymentFailedEvent for order {}: {}", 
                    event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
}
