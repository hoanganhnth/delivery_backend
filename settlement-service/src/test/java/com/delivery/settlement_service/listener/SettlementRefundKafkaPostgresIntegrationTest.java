package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.SettlementServiceApplication;
import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.repository.RefundCaseRepository;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves the feature-gated refund intake has the same Kafka/PostgreSQL replay
 * boundary as the core financial listener. Enabling this test-only consumer
 * must not create two cases/outbox rows across replica or group replays.
 */
@SpringBootTest(classes = SettlementServiceApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "app.refund.processing-enabled=true",
        "app.refund.provider-processing-enabled=false",
        "app.refund.outbox-relay-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class SettlementRefundKafkaPostgresIntegrationTest {

    private static final String TOPIC = "order.cancelled.settlement-refund-inbox-proof";
    private static final String DLT_TOPIC = TOPIC + ".DLT";
    private static final String REPLICA_GROUP = "settlement-refund-inbox-replicas";
    private static final String REPLAY_GROUP = "settlement-refund-inbox-replay";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("settlement_refund_kafka")
            .withUsername("settlement")
            .withPassword("settlement");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("app.kafka.topics.order-cancelled", () -> TOPIC);
        registry.add("spring.kafka.consumer.group-id", () -> REPLICA_GROUP);
    }

    @Autowired private RefundCaseRepository refundCaseRepository;
    @Autowired private RefundOutboxEventRepository refundOutboxRepository;
    @Autowired @Qualifier("retryKafkaTemplate") private KafkaTemplate<String, String> rawKafka;
    @Autowired private KafkaListenerEndpointRegistry primaryListenerRegistry;

    private final List<ConfigurableApplicationContext> replicas = new ArrayList<>();

    @BeforeEach
    void prepareBoundary() throws Exception {
        closeReplicas();
        stopPrimaryListeners();
        refundOutboxRepository.deleteAll();
        refundCaseRepository.deleteAll();
        createRequiredTopics();
    }

    @AfterEach
    void stopReplicas() {
        closeReplicas();
        stopPrimaryListeners();
    }

    @Test
    void kafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoRefundReplicas() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, "restaurant unavailable");

        startPrimaryReplica();
        startReplica(REPLICA_GROUP);
        await("two Settlement refund replicas to own both source partitions", () ->
                targetPartitionOwners(REPLICA_GROUP).equals(Set.of(0, 1)));

        rawKafka.send(TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        rawKafka.send(TOPIC, 1, "970001", payload).get(10, TimeUnit.SECONDS);

        await("one committed refund case before both source offsets", () ->
                refundCaseRepository.count() == 1
                        && refundOutboxRepository.count() == 0
                        && committedOffsetsAtLeast(REPLICA_GROUP, 1, 1));
        assertOneManualRefundCase();

        rawKafka.send(TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        await("same-group exact refund replay to commit as a no-op", () ->
                committedOffsetsAtLeast(REPLICA_GROUP, 2, 1));
        assertOneManualRefundCase();

        closeReplicas();
        stopPrimaryListeners();
        startReplica(REPLAY_GROUP);
        await("fresh refund group to replay historical offsets as no-ops", () ->
                committedOffsetsAtLeast(REPLAY_GROUP, 2, 1));
        assertOneManualRefundCase();

        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(DLT_TOPIC)) {
            String contradictory = payload(eventId, "contradictory cancellation reason");
            rawKafka.send(TOPIC, 1, "970001", contradictory).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> dlt = awaitRecord(
                    dltConsumer, "contradictory refund event reuse to reach the DLT");
            assertThat(dlt.value()).isEqualTo(contradictory);
            await("contradictory refund source offset to recover after DLT publication", () ->
                    committedOffsetsAtLeast(REPLAY_GROUP, 2, 2));
        }
        assertOneManualRefundCase();
    }

    private void assertOneManualRefundCase() {
        assertThat(refundCaseRepository.count()).isEqualTo(1);
        assertThat(refundOutboxRepository.count()).isZero();
        assertThat(refundCaseRepository.findAll()).singleElement().satisfies(refundCase -> {
            assertThat(refundCase.getStatus()).isEqualTo(RefundCase.RefundStatus.NO_REFUND_REQUIRED);
            assertThat(refundCase.getTrigger()).isEqualTo(RefundCase.RefundTrigger.ORDER_CANCELLED);
            assertThat(refundCase.getRefundAmount()).isEqualByComparingTo("0");
        });
    }

    private void startPrimaryReplica() {
        startListenersFor(primaryListenerRegistry);
    }

    private ConfigurableApplicationContext startReplica(String groupId) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SettlementServiceApplication.class)
                .web(WebApplicationType.NONE)
                .properties(replicaProperties(groupId))
                // The module test resource intentionally supplies an empty
                // broker value; command-line properties retain the disposable
                // broker for this independently booted replica.
                .run("--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers());
        startListenersFor(context.getBean(KafkaListenerEndpointRegistry.class));
        replicas.add(context);
        return context;
    }

    private Map<String, Object> replicaProperties(String groupId) {
        return Map.ofEntries(
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers()),
                Map.entry("spring.kafka.consumer.group-id", groupId),
                Map.entry("spring.kafka.listener.auto-startup", "false"),
                Map.entry("spring.kafka.admin.auto-create", "false"),
                Map.entry("spring.datasource.url", POSTGRES.getJdbcUrl()),
                Map.entry("spring.datasource.username", POSTGRES.getUsername()),
                Map.entry("spring.datasource.password", POSTGRES.getPassword()),
                Map.entry("spring.datasource.driver-class-name", "org.postgresql.Driver"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                Map.entry("spring.flyway.enabled", "true"),
                Map.entry("spring.flyway.baseline-on-migrate", "true"),
                Map.entry("app.kafka.topics.order-cancelled", TOPIC),
                Map.entry("spring.task.scheduling.enabled", "false"),
                Map.entry("spring.cloud.discovery.enabled", "false"),
                Map.entry("eureka.client.enabled", "false"),
                Map.entry("app.refund.processing-enabled", "true"),
                Map.entry("app.refund.provider-processing-enabled", "false"),
                Map.entry("app.refund.outbox-relay-enabled", "false"));
    }

    private void startListenersFor(KafkaListenerEndpointRegistry registry) {
        registry.getListenerContainers().stream()
                .filter(container -> listensTo(container, TOPIC))
                .forEach(container -> {
                    if (!container.isRunning()) {
                        container.start();
                    }
                });
    }

    private void stopPrimaryListeners() {
        primaryListenerRegistry.getListenerContainers().stream()
                .filter(container -> listensTo(container, TOPIC))
                .forEach(container -> {
                    if (container.isRunning()) {
                        container.stop();
                    }
                });
    }

    private boolean listensTo(MessageListenerContainer container, String topic) {
        String[] topics = container.getContainerProperties().getTopics();
        return topics != null && Arrays.asList(topics).contains(topic);
    }

    private Set<Integer> targetPartitionOwners(String groupId) {
        try (AdminClient admin = adminClient()) {
            ConsumerGroupDescription group = admin.describeConsumerGroups(List.of(groupId)).all()
                    .get(10, TimeUnit.SECONDS).get(groupId);
            return group.members().stream()
                    .flatMap(member -> member.assignment().topicPartitions().stream())
                    .filter(partition -> TOPIC.equals(partition.topic()))
                    .map(TopicPartition::partition)
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private boolean committedOffsetsAtLeast(String groupId, long partitionZero, long partitionOne) {
        try (AdminClient admin = adminClient()) {
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets =
                    admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                            .get(10, TimeUnit.SECONDS);
            return offsets.getOrDefault(new TopicPartition(TOPIC, 0),
                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0)).offset() >= partitionZero
                    && offsets.getOrDefault(new TopicPartition(TOPIC, 1),
                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0)).offset() >= partitionOne;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void createRequiredTopics() throws Exception {
        try (AdminClient admin = adminClient()) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC, 2, (short) 1),
                    new NewTopic(DLT_TOPIC, 2, (short) 1))).all().get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException exception) {
            if (!(exception.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException)) {
                throw exception;
            }
        }
    }

    private AdminClient adminClient() {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()));
    }

    private KafkaConsumer<String, String> freshConsumer(String topic) {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "settlement-refund-inbox-dlt-proof-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer,
                                                         String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                if (DLT_TOPIC.equals(record.topic())) {
                    return record;
                }
            }
        }
        fail("Timed out waiting for " + description);
        throw new AssertionError("unreachable");
    }

    private void await(String description, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for " + description, exception);
            }
        }
        fail("Timed out waiting for " + description);
    }

    private String payload(UUID eventId, String reason) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"ORDER_CANCELLED\""
                + ",\"orderId\":970001,\"userId\":7,\"restaurantId\":11"
                + ",\"previousStatus\":\"ASSIGNED\",\"currentStatus\":\"CANCELLED\""
                + ",\"cancelReason\":\"" + reason + "\",\"cancelledBy\":7"
                + ",\"cancelledBySource\":\"CUSTOMER\",\"cancelReasonCode\":\"CUSTOMER_CANCELLED\""
                + ",\"paymentMethod\":\"COD\",\"subtotalPrice\":100000"
                + ",\"discountAmount\":5000,\"shippingFee\":25000,\"totalPrice\":120000}";
    }

    private void closeReplicas() {
        for (int index = replicas.size() - 1; index >= 0; index--) {
            replicas.get(index).close();
        }
        replicas.clear();
    }
}
