package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleOutboxEvent;
import com.delivery.flashsale_service.repository.FlashSaleOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.delivery.flashsale.application.RelayUseCase;
import com.delivery.flashsale.application.api.RelayPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.flashsale.outbox-relay-enabled", havingValue = "true")
public class FlashSaleOutboxRelay {
    private final FlashSaleOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.flashsale.outbox-relay-ms:500}")
    @Transactional
    public void relay() {
        new RelayUseCase<>(new RelayPort<FlashSaleOutboxEvent>() {
            public LocalDateTime now() { return LocalDateTime.now(); }
            public List<FlashSaleOutboxEvent> due(LocalDateTime now, int limit) {
                return repository.lockDue(FlashSaleOutboxEvent.Status.PENDING, now, PageRequest.of(0, limit));
            }
            public void publish(FlashSaleOutboxEvent event) throws Exception {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(10, TimeUnit.SECONDS);
            }
            public void sent(FlashSaleOutboxEvent event, LocalDateTime now) {
                event.setStatus(FlashSaleOutboxEvent.Status.SENT); event.setSentAt(now); event.setLastError(null);
            }
            public int attempts(FlashSaleOutboxEvent event) { return event.getAttempts(); }
            public void failed(FlashSaleOutboxEvent event, int attempts, String error) { event.setAttempts(attempts); event.setLastError(error); }
            public void dead(FlashSaleOutboxEvent event, Exception cause) {
                event.setStatus(FlashSaleOutboxEvent.Status.DEAD); log.error("Flash outbox {} DEAD", event.getEventId(), cause);
            }
            public void retryAt(FlashSaleOutboxEvent event, LocalDateTime nextAttempt) { event.setNextAttemptAt(nextAttempt); }
        }).relay();
    }
}
