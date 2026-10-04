package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.dto.event.DeliveryExceptionReportedEvent;
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
 * Creates a review-only refund case from a post-pickup failure. It never calls
 * a provider or treats the event as proof that money should be refunded.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.refund.delivery-exception-processing-enabled", havingValue = "true")
public class DeliveryExceptionRefundListener {
    private final RefundCaseService refundCaseService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public DeliveryExceptionRefundListener(RefundCaseService refundCaseService) {
        this.refundCaseService = refundCaseService;
    }

    @KafkaListener(topics = "${app.kafka.topics.delivery-exception-reported:delivery.exception.reported}")
    @Transactional
    public void handleDeliveryException(String message,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                                        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
                                        Acknowledgment acknowledgment) {
        DeliveryExceptionReportedEvent event;
        try {
            event = objectMapper.readValue(message, DeliveryExceptionReportedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid delivery.exception.reported JSON", exception);
        }
        // The same dedicated stream carries state transitions for future
        // notification/audit consumers. Only the first RETRY_AVAILABLE fact
        // creates the manual-review case; later updates are acknowledged with
        // no financial side effect.
        if (!"DELIVERY_EXCEPTION_REPORTED".equals(event.getEventType())
                || !"RETRY_AVAILABLE".equals(event.getExceptionStatus())) {
            acknowledgeAfterCommit(acknowledgment);
            return;
        }
        refundCaseService.processDeliveryException(event);
        acknowledgeAfterCommit(acknowledgment);
        log.info("Processed post-pickup manual-review trigger: topic={}, deliveryId={}, eventId={}",
                topic, event.getDeliveryId(), event.getEventId());
    }

    private void acknowledgeAfterCommit(Acknowledgment acknowledgment) {
        if (acknowledgment == null) throw new IllegalArgumentException("Kafka acknowledgment is required");
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
