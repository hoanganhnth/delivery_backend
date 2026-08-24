package com.delivery.restaurant_service.listener;

import com.delivery.restaurant_service.service.MenuItemInventoryOrderEventProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.restaurant.inventory-enabled", havingValue = "true")
public class MenuItemInventoryOrderEventListener {

    private final MenuItemInventoryOrderEventProcessor processor;

    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.retry.max-delay-ms:10000}"),
            exclude = IllegalArgumentException.class,
            kafkaTemplate = "inventoryRetryKafkaTemplate",
            listenerContainerFactory = "inventoryKafkaListenerContainerFactory",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-inventory",
            dltTopicSuffix = ".inventory.DLT")
    @KafkaListener(
            topics = {"${app.kafka.topics.order-created:order.created}",
                    "${app.kafka.topics.order-cancelled:order.cancelled}",
                    "${app.kafka.topics.refund-eligible:order.refund-eligible}"},
            groupId = "${app.restaurant.inventory-consumer-group:restaurant-inventory-service-group}",
            containerFactory = "inventoryKafkaListenerContainerFactory",
            autoStartup = "${app.restaurant.inventory-consumer-enabled:true}")
    public void consume(String payload,
                        @Header(org.springframework.kafka.support.KafkaHeaders.RECEIVED_TOPIC) String topic,
                        Acknowledgment acknowledgment) throws Exception {
        processor.process(payload, topic);
        acknowledgment.acknowledge();
    }
}
