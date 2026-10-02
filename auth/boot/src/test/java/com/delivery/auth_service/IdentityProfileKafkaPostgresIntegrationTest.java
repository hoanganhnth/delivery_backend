package com.delivery.auth_service;

import com.delivery.auth.application.api.RegisterCommand;
import com.delivery.auth.application.api.RegistrationUseCase;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.IdentityInboxReceiptRepository;
import com.delivery.auth_service.repository.IdentityOutboxEventRepository;
import com.delivery.auth_service.service.SecurityEmailSender;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityProfileCreated;
import com.delivery.identity.contracts.IdentityStatusChanged;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AuthServiceApplication.class, properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.kafka.admin.auto-create=true", "spring.kafka.admin.fail-fast=true",
        "spring.kafka.listener.auto-startup=true", "app.identity.events.enabled=true",
        "app.identity.outbox.relay-enabled=true", "app.identity.outbox.poll-delay-ms=100",
        "app.identity.kafka.retry.auto-create-topics=true",
        "app.identity.public-registration-enabled=true", "app.user-status-sync.poll-delay-ms=3600000"
})
@Import(IdentityProfileKafkaPostgresIntegrationTest.Topics.class)
class IdentityProfileKafkaPostgresIntegrationTest {
    static final String PROFILE_TOPIC = "identity.profile.created.auth-runtime-proof";
    static final String GROUP = "auth-profile-runtime-proof";
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.identity.topics.profile-created", () -> PROFILE_TOPIC);
        registry.add("app.identity.profile-consumer-group", () -> GROUP);
        TestJwtKeyProperties.register(registry);
    }
    @Autowired RegistrationUseCase registration;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper mapper;
    @Autowired AuthAccountRepository accounts;
    @Autowired IdentityInboxReceiptRepository inbox;
    @Autowired IdentityOutboxEventRepository outbox;
    @MockitoBean SecurityEmailSender email;

    @Test void kafkaProfileEventCommitsOnceAndRelaysAuthoritativeStatus() throws Exception {
        Long id = registration.register(new RegisterCommand("kafka-auth-profile@example.test", "Password1!", "USER"))
                .account().id();
        UUID eventId = UUID.randomUUID();
        String raw = mapper.writeValueAsString(new IdentityProfileCreated(eventId, IdentityProfileCreated.TYPE,
                1, Instant.now(), UUID.randomUUID(), null, id, "USER_PROFILE", 9801L, 1L));
        try (var statusConsumer = new KafkaConsumer<String, String>(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "group.id", "auth-status-proof",
                "key.deserializer", StringDeserializer.class.getName(),
                "value.deserializer", StringDeserializer.class.getName(), "auto.offset.reset", "earliest"))) {
            statusConsumer.subscribe(List.of(IdentityStatusChanged.TYPE));
            kafka.send(PROFILE_TOPIC, id.toString(), raw).get(10, TimeUnit.SECONDS);
            await(() -> inbox.existsById(eventId));
            assertThat(accounts.findById(id).orElseThrow().getUserId()).isEqualTo(9801L);
            assertThat(accounts.findById(id).orElseThrow().getLifecycleStatus())
                    .isEqualTo(IdentityLifecycleStatus.PENDING_EMAIL_VERIFICATION);
            await(() -> outbox.findAll().stream().anyMatch(row -> row.getAggregateId().equals(id)
                    && row.getPublishedAt() != null));
            IdentityStatusChanged published = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (published == null && System.nanoTime() < deadline) {
                for (var record : statusConsumer.poll(Duration.ofMillis(200))) {
                    var candidate = mapper.readValue(record.value(), IdentityStatusChanged.class);
                    if (candidate.principalId().equals(id)) published = candidate;
                }
            }
            assertThat(published).isNotNull();
            assertThat(published.status()).isEqualTo(IdentityLifecycleStatus.PENDING_EMAIL_VERIFICATION);
            assertThat(published.lifecycleVersion()).isEqualTo(1L);
            kafka.send(PROFILE_TOPIC, id.toString(), raw).get(10, TimeUnit.SECONDS);
            try (var admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
                boolean replayCommitted = false;
                long committedDeadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                while (!replayCommitted && System.nanoTime() < committedDeadline) {
                    replayCommitted = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                            .get(10, TimeUnit.SECONDS).values().stream().anyMatch(offset -> offset.offset() >= 2);
                    if (!replayCommitted) Thread.sleep(50);
                }
                assertThat(replayCommitted).isTrue();
            }
            assertThat(outbox.findAll().stream().filter(row -> row.getAggregateId().equals(id))).hasSize(1);
        }
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Auth Kafka/PostgreSQL state did not converge");
    }

    @TestConfiguration static class Topics {
        @Bean org.apache.kafka.clients.admin.NewTopic profileTopic() {
            return TopicBuilder.name(PROFILE_TOPIC).partitions(1).replicas(1).build();
        }
        @Bean org.apache.kafka.clients.admin.NewTopic statusTopic() {
            return TopicBuilder.name(IdentityStatusChanged.TYPE).partitions(1).replicas(1).build();
        }
    }
}
