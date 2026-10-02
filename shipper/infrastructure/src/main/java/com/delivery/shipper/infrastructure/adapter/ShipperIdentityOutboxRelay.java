package com.delivery.shipper.infrastructure.adapter;

import com.delivery.shipper.application.api.ShipperPorts;
import com.delivery.shipper.infrastructure.entity.ShipperIdentityOutboxEvent;
import com.delivery.shipper.infrastructure.repository.ShipperIdentityOutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Gated, synchronous Kafka relay for the shipper identity outbox. */
@Component
public class ShipperIdentityOutboxRelay implements ShipperPorts.IdentityOutboxRelay {
    private final ShipperIdentityOutboxEventRepository events; private final KafkaTemplate<String, String> kafka;
    private final ShipperIdentityOutboxService outbox; private final boolean enabled;
    private final Counter published; private final Counter failed;

    public ShipperIdentityOutboxRelay(ShipperIdentityOutboxEventRepository events, KafkaTemplate<String, String> kafka,
            ShipperIdentityOutboxService outbox,
            @Value("${app.shipper.identity-outbox.relay-enabled:false}") boolean enabled, MeterRegistry metrics) {
        this.events = events; this.kafka = kafka; this.outbox = outbox; this.enabled = enabled;
        published = Counter.builder("delivery.identity.outbox.relay").tag("owner", "shipper").tag("outcome", "published").register(metrics);
        failed = Counter.builder("delivery.identity.outbox.relay").tag("owner", "shipper").tag("outcome", "failed").register(metrics);
        Gauge.builder("delivery.identity.outbox.pending", events, ShipperIdentityOutboxEventRepository::pendingCount).tag("owner", "shipper").register(metrics);
        Gauge.builder("delivery.identity.outbox.oldest.age", events, e -> oldestAgeSeconds(e.oldestPendingCreatedAt())).tag("owner", "shipper").register(metrics);
    }

    @Scheduled(fixedDelayString = "${app.shipper.identity-outbox.poll-delay-ms:1000}")
    @Transactional
    void relay() { relayBatch(50); }

    @Override
    @Transactional
    public int relayBatch(int maxItems) {
        if (!enabled) return 0;
        outbox.seedExisting(maxItems);
        List<ShipperIdentityOutboxEvent> ready = events.findReady(LocalDateTime.now(), PageRequest.of(0, maxItems));
        int processed = 0;
        for (ShipperIdentityOutboxEvent event : ready) try {
            kafka.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
            event.setPublishedAt(LocalDateTime.now()); published.increment(); processed++;
        } catch (Exception failure) {
            event.setAttempts(event.getAttempts() + 1);
            event.setAvailableAt(LocalDateTime.now().plusSeconds(Math.min(60, 1L << Math.min(6, event.getAttempts())))); failed.increment();
        }
        return processed;
    }

    private static double oldestAgeSeconds(LocalDateTime createdAt) {
        return createdAt == null ? 0D : Math.max(0D, Duration.between(createdAt, LocalDateTime.now()).toMillis() / 1000D);
    }
}
