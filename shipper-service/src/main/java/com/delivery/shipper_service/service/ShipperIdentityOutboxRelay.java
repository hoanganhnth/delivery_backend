package com.delivery.shipper_service.service;

import com.delivery.shipper_service.entity.ShipperIdentityOutboxEvent;
import com.delivery.shipper_service.repository.ShipperIdentityOutboxEventRepository;
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
public class ShipperIdentityOutboxRelay {
    private final ShipperIdentityOutboxEventRepository events; private final KafkaTemplate<String, String> kafka;
    private final ShipperIdentityOutboxService outbox;
    private final boolean enabled;
    private final Counter published;
    private final Counter failed;
    public ShipperIdentityOutboxRelay(ShipperIdentityOutboxEventRepository events, KafkaTemplate<String, String> kafka,
            ShipperIdentityOutboxService outbox,
            @Value("${app.shipper.identity-outbox.relay-enabled:false}") boolean enabled, MeterRegistry metrics) {
        this.events = events; this.kafka = kafka; this.outbox = outbox; this.enabled = enabled;
        this.published = Counter.builder("delivery.identity.outbox.relay").tag("owner", "shipper").tag("outcome", "published").register(metrics);
        this.failed = Counter.builder("delivery.identity.outbox.relay").tag("owner", "shipper").tag("outcome", "failed").register(metrics);
        Gauge.builder("delivery.identity.outbox.pending", events, ShipperIdentityOutboxEventRepository::pendingCount)
                .tag("owner", "shipper").register(metrics);
        Gauge.builder("delivery.identity.outbox.oldest.age", events, repository -> oldestAgeSeconds(repository.oldestPendingCreatedAt()))
                .tag("owner", "shipper").register(metrics);
    }
    @Scheduled(fixedDelayString = "${app.shipper.identity-outbox.poll-delay-ms:1000}")
    @Transactional
    void relay() {
        if (!enabled) return;
        outbox.seedExisting(50);
        List<ShipperIdentityOutboxEvent> ready = events.findReady(LocalDateTime.now(), PageRequest.of(0, 50));
        for (ShipperIdentityOutboxEvent event : ready) try {
            kafka.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
            event.setPublishedAt(LocalDateTime.now());
            published.increment();
        } catch (Exception failure) {
            event.setAttempts(event.getAttempts() + 1);
            event.setAvailableAt(LocalDateTime.now().plusSeconds(Math.min(60, 1L << Math.min(6, event.getAttempts()))));
            failed.increment();
        }
    }

    private static double oldestAgeSeconds(LocalDateTime createdAt) {
        return createdAt == null ? 0D : Math.max(0D, Duration.between(createdAt, LocalDateTime.now()).toMillis() / 1000D);
    }
}
