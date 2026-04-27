package com.delivery.delivery_service.service;

import com.delivery.delivery_service.entity.OutboxEvent;
import com.delivery.delivery_service.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ✅ Outbox Service — Lưu event vào bảng outbox (gọi trong cùng @Transactional)
 *
 * TRƯỚC: kafkaTemplate.send(topic, key, event)  ← Mất event nếu server sập
 * SAU:   outboxService.saveEvent(topic, key, event)  ← Atomic với business logic
 */
@Slf4j
//@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Support LocalDateTime
    }

    /**
     * Lưu event vào outbox table — PHẢI được gọi trong cùng @Transactional với business logic
     */
    public void saveEvent(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            OutboxEvent event = OutboxEvent.builder()
                    .topic(topic)
                    .eventKey(key)
                    .payload(json)
                    .build();

            outboxEventRepository.save(event);
            log.debug("📦 [Outbox] Saved event to outbox: topic={}, key={}", topic, key);

        } catch (Exception e) {
            log.error("💥 [Outbox] Failed to save event: topic={}, error={}", topic, e.getMessage(), e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }
}
