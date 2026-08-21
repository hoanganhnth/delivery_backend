package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.IdentityOutboxEvent;
import com.delivery.auth_service.repository.IdentityOutboxEventRepository;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityOutboxRelay {
    private final IdentityOutboxEventRepository events; private final KafkaTemplate<String, String> kafka; private final boolean enabled;
    private final Counter published; private final Counter failed;
    public IdentityOutboxRelay(IdentityOutboxEventRepository events, KafkaTemplate<String, String> kafka,
            @Value("${app.identity.outbox.relay-enabled:false}") boolean enabled, MeterRegistry metrics) {
        this.events = events; this.kafka = kafka; this.enabled = enabled;
        this.published = Counter.builder("delivery.identity.outbox.relay").tag("owner", "auth").tag("outcome", "published").register(metrics);
        this.failed = Counter.builder("delivery.identity.outbox.relay").tag("owner", "auth").tag("outcome", "failed").register(metrics);
        Gauge.builder("delivery.identity.outbox.pending", events, IdentityOutboxEventRepository::pendingCount)
                .tag("owner", "auth").register(metrics);
        Gauge.builder("delivery.identity.outbox.oldest.age", events, repository -> oldestAgeSeconds(repository.oldestPendingCreatedAt()))
                .tag("owner", "auth").register(metrics);
    }
    @Scheduled(fixedDelayString = "${app.identity.outbox.poll-delay-ms:1000}")
    @Transactional
    void relay() {
        if (!enabled) return;
        List<IdentityOutboxEvent> ready = events.findReady(LocalDateTime.now(), PageRequest.of(0, 50));
        for (IdentityOutboxEvent event : ready) {
            try { kafka.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(); event.setPublishedAt(LocalDateTime.now()); published.increment(); }
            catch (Exception failure) { event.setAttempts(event.getAttempts() + 1); event.setAvailableAt(LocalDateTime.now().plusSeconds(Math.min(60, 1L << Math.min(6, event.getAttempts())))); failed.increment(); }
        }
    }

    private static double oldestAgeSeconds(LocalDateTime createdAt) {
        return createdAt == null ? 0D : Math.max(0D, Duration.between(createdAt, LocalDateTime.now()).toMillis() / 1000D);
    }
}
