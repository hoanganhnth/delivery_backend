package com.delivery.livestream_service.service;

import com.delivery.livestream_service.common.constants.KafkaTopicConstants;
import com.delivery.livestream_service.dto.event.LivestreamEndedEvent;
import com.delivery.livestream_service.dto.event.LivestreamStartedEvent;
import com.delivery.livestream_service.dto.event.ProductPinnedEvent;
import com.delivery.livestream_service.dto.event.ProductUnpinnedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LivestreamEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LivestreamEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishLivestreamStarted(LivestreamStartedEvent event) {
        log.info("Publishing LivestreamStartedEvent: livestreamId={}", event.getLivestreamId());
        kafkaTemplate.send(KafkaTopicConstants.LIVESTREAM_STARTED_TOPIC, event.getLivestreamId().toString(), event);
    }

    public void publishLivestreamEnded(LivestreamEndedEvent event) {
        log.info("Publishing LivestreamEndedEvent: livestreamId={}", event.getLivestreamId());
        kafkaTemplate.send(KafkaTopicConstants.LIVESTREAM_ENDED_TOPIC, event.getLivestreamId().toString(), event);
    }

    public void publishProductPinned(ProductPinnedEvent event) {
        log.info("Publishing ProductPinnedEvent: livestreamId={}, productId={}", 
                event.getLivestreamId(), event.getProductId());
        kafkaTemplate.send(KafkaTopicConstants.PRODUCT_PINNED_TOPIC, event.getLivestreamId().toString(), event);
    }

    public void publishProductUnpinned(ProductUnpinnedEvent event) {
        log.info("Publishing ProductUnpinnedEvent: livestreamId={}, productId={}", 
                event.getLivestreamId(), event.getProductId());
        kafkaTemplate.send(KafkaTopicConstants.PRODUCT_UNPINNED_TOPIC, event.getLivestreamId().toString(), event);
    }
}
