package com.delivery.delivery_service.service;

import com.delivery.delivery_service.entity.OutboxEvent;
import com.delivery.delivery_service.entity.OutboxEvent.OutboxStatus;
import com.delivery.delivery_service.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ✅ Outbox Message Relay — Poll DB mỗi 1 giây, gửi event lên Kafka
 *
 * Flow:
 * 1. Poll bảng outbox_events WHERE status = PENDING (FIFO, max 100)
 * 2. Gửi lên Kafka
 * 3. Nếu thành công → đánh dấu SENT
 * 4. Nếu thất bại → tăng retryCount, giữ PENDING (retry lần sau)
 * 5. Nếu retry > 10 → đánh dấu FAILED (cần xử lý thủ công)
 */
@Slf4j
//@Component
public class OutboxMessageRelay {

    private static final int MAX_RETRY = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxMessageRelay(OutboxEventRepository outboxEventRepository,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Poll outbox mỗi 1 giây
     */
    @Scheduled(fixedRate = 1000)
    public void relayMessages() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) return;

        log.debug("📤 [Outbox Relay] Found {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Parse payload string back to JsonNode to avoid double-serialization
                JsonNode jsonPayload = objectMapper.readTree(event.getPayload());

                // Gửi lên Kafka (sync để đảm bảo thành công)
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), jsonPayload).get();

                // Thành công → SENT
                event.setStatus(OutboxStatus.SENT);
                event.setSentAt(LocalDateTime.now());
                outboxEventRepository.save(event);

                log.info("✅ [Outbox Relay] Sent event #{} to topic={}, key={}",
                        event.getId(), event.getTopic(), event.getEventKey());

            } catch (Exception e) {
                // Thất bại → tăng retry
                event.setRetryCount(event.getRetryCount() + 1);
                event.setErrorMessage(e.getMessage());

                if (event.getRetryCount() >= MAX_RETRY) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("🚨 [Outbox Relay] Event #{} FAILED after {} retries: topic={}, error={}",
                            event.getId(), MAX_RETRY, event.getTopic(), e.getMessage());
                } else {
                    log.warn("⚠️ [Outbox Relay] Event #{} retry {}/{}: topic={}, error={}",
                            event.getId(), event.getRetryCount(), MAX_RETRY,
                            event.getTopic(), e.getMessage());
                }

                outboxEventRepository.save(event);
            }
        }
    }
}
