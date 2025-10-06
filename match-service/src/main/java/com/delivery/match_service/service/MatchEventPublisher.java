package com.delivery.match_service.service;

import com.delivery.match_service.common.constants.KafkaTopicConstants;
import com.delivery.match_service.dto.event.ShipperNotFoundEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * ✅ Event Publisher cho Match Service
 * Bắn events khi không tìm được shipper
 */
@Service
@Slf4j
public class MatchEventPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public MatchEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * ✅ Bắn event khi không tìm được shipper sau nhiều lần retry
     */
    public void publishShipperNotFoundEvent(ShipperNotFoundEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String key = "delivery_" + event.getDeliveryId();
            
            log.info("📤 Publishing ShipperNotFoundEvent for delivery: {} to topic: {}", 
                    event.getDeliveryId(), KafkaTopicConstants.SHIPPER_NOT_FOUND_TOPIC);
            
            // ✅ Async publishing với callback
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    KafkaTopicConstants.SHIPPER_NOT_FOUND_TOPIC, key, eventJson);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✅ Successfully published ShipperNotFoundEvent for delivery: {} - offset: {}", 
                            event.getDeliveryId(), result.getRecordMetadata().offset());
                } else {
                    log.error("💥 Failed to publish ShipperNotFoundEvent for delivery: {}: {}", 
                             event.getDeliveryId(), ex.getMessage(), ex);
                }
            });
            
        } catch (JsonProcessingException e) {
            log.error("💥 Error serializing ShipperNotFoundEvent for delivery: {}: {}", 
                     event.getDeliveryId(), e.getMessage(), e);
        }
    }
}
