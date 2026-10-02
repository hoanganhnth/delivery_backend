package com.delivery.user_service;

import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityStatusChanged;
import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.UserProfileUseCase;
import com.delivery.user_service.repository.IdentityInboxReceiptRepository;
import com.delivery.user_service.repository.UserRepository;
import com.delivery.user_service.repository.IdentityOutboxEventRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(classes = UserServiceApplication.class, properties = {
        "spring.config.location=file:src/main/resources/application.properties",
        "spring.config.import=", "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false", "management.server.port=0", "server.port=0",
        "spring.kafka.admin.auto-create=true", "spring.kafka.admin.fail-fast=true",
        "spring.kafka.listener.auto-startup=true", "app.identity.events.enabled=true",
        "app.identity.kafka.retry.auto-create-topics=true",
        "app.identity.kafka.retry.initial-delay-ms=50", "app.identity.kafka.retry.max-delay-ms=50",
        "app.identity.kafka.retry.multiplier=2", "app.identity.outbox.relay-enabled=true",
        "app.identity.outbox.poll-delay-ms=100"
})
@Import(UserIdentityKafkaPostgresIntegrationTest.Topics.class)
class UserIdentityKafkaPostgresIntegrationTest {
    static final String TOPIC = "identity.status.changed.user-runtime-proof";
    static final String GROUP = "user-identity-runtime-proof";
    static final String DLT = TOPIC + ".user-identity.DLT";
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
    @Autowired UserProfileUseCase create;
    @Autowired UserRepository profiles;
    @Autowired IdentityInboxReceiptRepository receipts;
    @Autowired IdentityOutboxEventRepository outbox;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper mapper;

    @Test void rawIdentityEventsReplayCommitOffsetsAndSendConflictsAndGapsToDlt() throws Exception {
        var profile = create.create(new CreateUserCommand(970L, 970L, "kafka@example.test", "USER",
                "Kafka Fixture", null, null, null, null));
        await(() -> outbox.findAll().stream().anyMatch(row -> row.getAggregateId().equals(970L) && row.getPublishedAt() != null));
        UUID first = UUID.randomUUID();
        String active = raw(first, IdentityLifecycleStatus.ACTIVE, 5);
        kafka.send(TOPIC, "970", active).get(10, TimeUnit.SECONDS);
        await(() -> receipts.existsById(first));
        UUID blockedId = UUID.randomUUID();
        String blocked = raw(blockedId, IdentityLifecycleStatus.BLOCKED, 6);
        kafka.send(TOPIC, "970", blocked).get(10, TimeUnit.SECONDS);
        await(() -> receipts.existsById(blockedId));
        var persisted = profiles.findByPrincipalId(970L).orElseThrow();
        assertThat(persisted.getIdentityStatusVersion()).isEqualTo(6);
        assertThat(persisted.getIsBlocked()).isTrue();
        assertThat(persisted.getIsActive()).isFalse();
        assertThat(persisted.getBlockedAt()).isNotNull();
        kafka.send(TOPIC, "970", blocked).get(10, TimeUnit.SECONDS);
        String conflict = raw(blockedId, IdentityLifecycleStatus.ACTIVE, 7);
        String gap = raw(UUID.randomUUID(), IdentityLifecycleStatus.ACTIVE, 8);
        try (var dlt = new KafkaConsumer<String, String>(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "group.id", "user-dlt-proof",
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
        assertThat(profiles.findByPrincipalId(970L).orElseThrow().getIdentityStatusVersion()).isEqualTo(6);
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertThat(committed.values()).anySatisfy(offset -> assertThat(offset.offset()).isGreaterThanOrEqualTo(5));
        }
        UUID unblocked = UUID.randomUUID();
        kafka.send(TOPIC, "970", raw(unblocked, IdentityLifecycleStatus.ACTIVE, 7)).get(10, TimeUnit.SECONDS);
        await(() -> receipts.existsById(unblocked));
        var restored = profiles.findByPrincipalId(970L).orElseThrow();
        assertThat(restored.getIdentityStatusVersion()).isEqualTo(7);
        assertThat(restored.getIsActive()).isTrue();
        assertThat(restored.getIsBlocked()).isFalse();
        assertThat(restored.getBlockedAt()).isNull();
        assertThat(restored.getBlockedBy()).isNull();
        assertThat(restored.getBlockReason()).isNull();
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
        throw new AssertionError("User Kafka/Postgres state did not converge");
    }
    @TestConfiguration static class Topics {
        @Bean NewTopic identityStatusProofTopic() { return new NewTopic(TOPIC, 1, (short) 1); }
        @Bean NewTopic identityOutboxTopic() { return new NewTopic(com.delivery.identity.contracts.IdentityProfileCreated.TYPE, 1, (short) 1); }
    }
}
