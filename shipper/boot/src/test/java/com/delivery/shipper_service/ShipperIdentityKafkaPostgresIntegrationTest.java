package com.delivery.shipper_service;

import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityStatusChanged;
import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperPorts;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.domain.identity.ShipperRole;
import com.delivery.shipper.infrastructure.repository.IdentityInboxReceiptRepository;
import com.delivery.shipper.infrastructure.repository.ShipperRepository;
import com.delivery.shipper.infrastructure.repository.ShipperIdentityOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(classes = ShipperServiceApplication.class, properties = {
        "spring.config.location=file:src/main/resources/application.properties",
        "spring.config.import=", "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false", "management.server.port=0", "server.port=0",
        "spring.kafka.admin.auto-create=true", "spring.kafka.admin.fail-fast=true",
        "spring.kafka.listener.auto-startup=true", "app.identity.events.enabled=true",
        "app.identity.kafka.retry.auto-create-topics=true",
        "app.identity.kafka.retry.initial-delay-ms=50", "app.identity.kafka.retry.max-delay-ms=50",
        "app.identity.kafka.retry.multiplier=2", "app.shipper.identity-outbox.relay-enabled=true",
        "app.shipper.identity-outbox.poll-delay-ms=100"
})
@Import(ShipperIdentityKafkaPostgresIntegrationTest.Topics.class)
class ShipperIdentityKafkaPostgresIntegrationTest {
    static final String TOPIC = "identity.status.changed.shipper-runtime-proof";
    static final String GROUP = "shipper-identity-runtime-proof";
    static final String DLT = TOPIC + ".shipper-identity.DLT";
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.identity.topics.status-changed", () -> TOPIC);
        registry.add("app.identity.status-consumer-group", () -> GROUP);
    }
    @Autowired ShipperUseCases.CreateProfile create;
    @Autowired ShipperUseCases.SetOnlineStatus online;
    @Autowired ShipperRepository profiles;
    @Autowired IdentityInboxReceiptRepository receipts;
    @Autowired ShipperIdentityOutboxEventRepository outbox;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper mapper;
    @MockitoBean ShipperPorts.TrackingAvailability tracking;

    @Test void rawIdentityEventsReplayCommitOffsetsAndSendConflictsAndGapsToDlt() throws Exception {
        var actor = new ShipperCommands.Actor(970, 1970L, ShipperRole.SHIPPER);
        var profile = create.execute(new ShipperCommands.CreateProfile(actor, "Kafka Fixture", "BIKE", "KAFKA-LICENSE", "KAFKA-CARD", "0900000000", "TEST", null, null, null, null)).profile();
        await(() -> outbox.findAll().stream().anyMatch(row -> row.getAggregateId().equals(profile.id()) && row.getPublishedAt() != null));
        UUID first = UUID.randomUUID();
        String active = raw(first, IdentityLifecycleStatus.ACTIVE, 1);
        kafka.send(TOPIC, "970", active).get(10, TimeUnit.SECONDS);
        await(() -> receipts.existsById(first));
        online.execute(new ShipperCommands.SetOnlineStatus(actor, true));
        UUID blockedId = UUID.randomUUID();
        String blocked = raw(blockedId, IdentityLifecycleStatus.BLOCKED, 2);
        kafka.send(TOPIC, "970", blocked).get(10, TimeUnit.SECONDS);
        await(() -> receipts.existsById(blockedId));
        var persisted = profiles.findByPrincipalId(970L).orElseThrow();
        assertThat(persisted.getIdentityStatusVersion()).isEqualTo(2);
        assertThat(persisted.getIsOnline()).isFalse();
        verify(tracking).markOffline(eq(profile.id()), anyLong());
        kafka.send(TOPIC, "970", blocked).get(10, TimeUnit.SECONDS);
        String conflict = raw(blockedId, IdentityLifecycleStatus.ACTIVE, 3);
        String gap = raw(UUID.randomUUID(), IdentityLifecycleStatus.ACTIVE, 4);
        try (var dlt = new KafkaConsumer<String, String>(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "group.id", "shipper-dlt-proof",
                "key.deserializer", StringDeserializer.class.getName(), "value.deserializer", StringDeserializer.class.getName(),
                "auto.offset.reset", "earliest"))) {
            dlt.subscribe(java.util.List.of(DLT));
            kafka.send(TOPIC, "970", conflict).get(10, TimeUnit.SECONDS);
            kafka.send(TOPIC, "970", gap).get(10, TimeUnit.SECONDS);
            var failed = new java.util.HashSet<String>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (failed.size() < 2 && System.nanoTime() < deadline) {
                dlt.poll(Duration.ofMillis(200)).forEach(record -> failed.add(record.value()));
            }
            assertThat(failed).contains(conflict, gap);
        }
        assertThat(receipts.count()).isEqualTo(2);
        assertThat(profiles.findByPrincipalId(970L).orElseThrow().getIdentityStatusVersion()).isEqualTo(2);
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertThat(committed.values()).anySatisfy(offset -> assertThat(offset.offset()).isGreaterThanOrEqualTo(5));
        }
        // A failed Tracking convergence must roll back both the projection and receipt.
        online.execute(new ShipperCommands.SetOnlineStatus(actor, true));
        doThrow(new IllegalStateException("fixture Tracking unavailable")).when(tracking).markOffline(eq(profile.id()), anyLong());
        UUID unavailableId = UUID.randomUUID();
        String unavailable = raw(unavailableId, IdentityLifecycleStatus.BLOCKED, 3);
        try (var dlt = new KafkaConsumer<String, String>(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "group.id", "shipper-tracking-failure-proof",
                "key.deserializer", StringDeserializer.class.getName(), "value.deserializer", StringDeserializer.class.getName(),
                "auto.offset.reset", "earliest"))) {
            dlt.subscribe(java.util.List.of(DLT));
            kafka.send(TOPIC, "970", unavailable).get(10, TimeUnit.SECONDS);
            boolean deadLettered = false;
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (!deadLettered && System.nanoTime() < deadline) {
                for (var record : dlt.poll(Duration.ofMillis(200))) {
                    if (unavailable.equals(record.value())) deadLettered = true;
                }
            }
            assertThat(deadLettered).isTrue();
        }
        assertThat(receipts.existsById(unavailableId)).isFalse();
        var unchanged = profiles.findByPrincipalId(970L).orElseThrow();
        assertThat(unchanged.getIdentityStatusVersion()).isEqualTo(2);
        assertThat(unchanged.getIsOnline()).isTrue();

    }

    private String raw(UUID id, IdentityLifecycleStatus status, long version) throws Exception {
        return mapper.writeValueAsString(new IdentityStatusChanged(id, IdentityStatusChanged.TYPE, 1, Instant.EPOCH,
                id, null, 970L, status, version, "TEST", null));
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Shipper Kafka/Postgres state did not converge");
    }
    @TestConfiguration static class Topics {
        @Bean NewTopic identityStatusProofTopic() { return new NewTopic(TOPIC, 1, (short) 1); }
        @Bean NewTopic identityOutboxTopic() { return new NewTopic("shipper.identity.upserted", 1, (short) 1); }
    }
}
