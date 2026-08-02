package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.dto.event.OrderCancelledEvent;
import com.delivery.settlement_service.service.RefundCaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Consumes the canonical cancellation snapshot only when the refund boundary is
 * explicitly enabled. Provider execution remains separately disabled by default.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.refund.processing-enabled", havingValue = "true")
public class OrderCancelledRefundListener {
    private final RefundCaseService refundCaseService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public OrderCancelledRefundListener(RefundCaseService refundCaseService) {
        this.refundCaseService = refundCaseService;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-cancelled:order.cancelled}")
    @Transactional
    public void handleOrderCancelled(String message,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                                     @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
                                     Acknowledgment acknowledgment) {
        OrderCancelledEvent event;
        try {
            event = objectMapper.readValue(message, OrderCancelledEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid order.cancelled JSON", exception);
        }

        refundCaseService.processOrderCancellation(event);
        acknowledgeAfterCommit(acknowledgment);
        log.info("Processed order.cancelled for refund boundary: orderId={}, eventId={}",
                event.getOrderId(), event.getEventId());
    }

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
