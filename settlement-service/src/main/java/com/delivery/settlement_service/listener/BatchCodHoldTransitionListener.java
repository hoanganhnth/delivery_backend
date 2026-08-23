package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.entity.CodCapacityHoldStatus;
import com.delivery.settlement_service.service.CodCapacityHoldService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Applies Delivery's durable batch accept/release intent to Settlement holds. */
@Component
@Slf4j
public class BatchCodHoldTransitionListener {
    private final CodCapacityHoldService holdService;
    private final ObjectMapper objectMapper;

    public BatchCodHoldTransitionListener(CodCapacityHoldService holdService, ObjectMapper objectMapper) {
        this.holdService = holdService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.batch-accepted:delivery.batch.accepted}")
    @Transactional
    public void handle(String message, Acknowledgment acknowledgment) {
        transition(message, acknowledgment);
    }

    @KafkaListener(topics = "${app.kafka.topics.batch-released:delivery.batch.released}")
    @Transactional
    public void handleRelease(String message, Acknowledgment acknowledgment) {
        transition(message, acknowledgment);
    }

    private void transition(String message, Acknowledgment acknowledgment) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String target = root.path("target").asText();
            CodCapacityHoldStatus status = CodCapacityHoldStatus.valueOf(target);
            JsonNode ids = root.path("holdIds");
            if (!ids.isArray() || ids.isEmpty()) throw new IllegalArgumentException("Batch hold IDs are required");
            for (JsonNode id : ids) holdService.transition(UUID.fromString(id.asText()), status);
            acknowledgment.acknowledge();
        } catch (IllegalArgumentException ex) {
            log.error("Invalid batch COD hold transition: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot apply batch COD hold transition", ex);
        }
    }
}
