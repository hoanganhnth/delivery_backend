package com.delivery.promotion_service.listener;

import com.delivery.promotion_service.service.PromotionOrderReservationEventProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.retry.annotation.Backoff;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "app.promotion.checkout-enabled", havingValue = "true")
public class OrderReservationEventListener {
    private final PromotionOrderReservationEventProcessor processor;

    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.retry.max-delay-ms:10000}"),
            exclude = IllegalArgumentException.class,
            kafkaTemplate = "retryKafkaTemplate",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-promotion",
            dltTopicSuffix = ".promotion.DLT")
    @KafkaListener(topics = {"${app.kafka.topics.order-created:order.created}",
            "${app.kafka.topics.order-cancelled:order.cancelled}",
            "${app.kafka.topics.refund-eligible:order.refund-eligible}"})
    public void consume(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        Acknowledgment acknowledgment) throws Exception {
        processor.process(payload, topic);
        acknowledgment.acknowledge();
    }
}
