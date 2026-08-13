package com.delivery.settlement_service.listener;

import com.delivery.settlement_service.SettlementServiceApplication;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.SettlementReceiptRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
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
 * Exercises the real financial Kafka boundary against PostgreSQL. Two
 * independently booted Settlement replicas share a consumer group and receive
 * one completion identity from separate partitions. Exact replay must produce
 * one receipt/four ledger entries; contradictory event-ID reuse must retain
 * the raw source record in the same-partition DLT.
 */
@SpringBootTest(classes = SettlementServiceApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "app.refund.processing-enabled=false",
        "app.refund.outbox-relay-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class SettlementKafkaPostgresIntegrationTest {

    private static final String TOPIC = "delivery.completed.settlement-inbox-proof";
    private static final String DLT_TOPIC = TOPIC + ".DLT";
    private static final String REPLICA_GROUP = "settlement-inbox-replicas";
    private static final String REPLAY_GROUP = "settlement-inbox-replay";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("settlement_kafka")
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
        registry.add("app.kafka.topics.delivery-completed", () -> TOPIC);
        registry.add("spring.kafka.consumer.group-id", () -> REPLICA_GROUP);
    }

    @Autowired private SettlementReceiptRepository receiptRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BalanceRepository balanceRepository;
    @Autowired @Qualifier("retryKafkaTemplate") private KafkaTemplate<String, String> rawKafka;
    @Autowired private KafkaListenerEndpointRegistry primaryListenerRegistry;

    private final List<ConfigurableApplicationContext> replicas = new ArrayList<>();

    @BeforeEach
    void prepareBoundary() throws Exception {
        closeReplicas();
        stopPrimaryListeners();
        transactionRepository.deleteAll();
        receiptRepository.deleteAll();
        balanceRepository.deleteAll();
        createRequiredTopics();
    }

    @AfterEach
    void stopReplicas() {
        closeReplicas();
        stopPrimaryListeners();
    }

    @Test
    void kafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoSettlementReplicas() throws Exception {
        balanceRepository.saveAndFlush(Balance.builder()
                .entityId(22L)
                .entityType(EntityType.SHIPPER)
                .depositBalance(java.math.BigDecimal.valueOf(120_000))
                .totalDeposited(java.math.BigDecimal.valueOf(120_000))
                .build());

        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, 970_001L, "Restaurant A");

        // Use the primary test context as one replica to stay within the
        // bounded resource budget while still exercising a true two-member
        // Kafka group.
        startPrimaryReplica();
        startReplica(REPLICA_GROUP);
        await("two Settlement replicas to own the two source partitions", () ->
                targetPartitionOwners(REPLICA_GROUP).equals(Set.of(0, 1)));

        rawKafka.send(TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        rawKafka.send(TOPIC, 1, "970001", payload).get(10, TimeUnit.SECONDS);

        await("one committed settlement receipt and ledger before both source offsets", () ->
                receiptRepository.count() == 1
                        && transactionRepository.count() == 4
                        && committedOffsetsAtLeast(REPLICA_GROUP, 1, 1));
        assertOneFinancialEffect();

        rawKafka.send(TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        await("same-group exact replay to commit as a financial no-op", () ->
                committedOffsetsAtLeast(REPLICA_GROUP, 2, 1));
        assertOneFinancialEffect();

        closeReplicas();
        stopPrimaryListeners();
        startReplica(REPLAY_GROUP);
        await("fresh Settlement group to replay historical offsets as no-ops", () ->
                committedOffsetsAtLeast(REPLAY_GROUP, 2, 1));
        assertOneFinancialEffect();

        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(DLT_TOPIC)) {
            String contradictory = payload(eventId, 970_001L, "Contradictory restaurant");
            rawKafka.send(TOPIC, 1, "970001", contradictory).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> dlt = awaitRecord(
                    dltConsumer, "contradictory Settlement receipt reuse to reach the DLT");
            assertThat(dlt.value()).isEqualTo(contradictory);
            await("contradictory source offset to recover after DLT publication", () ->
                    committedOffsetsAtLeast(REPLAY_GROUP, 2, 2));
        }
        assertOneFinancialEffect();
    }

    private void assertOneFinancialEffect() {
        assertThat(receiptRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(4);
        assertThat(transactionRepository.findAll())
                .extracting(Transaction::getReason)
                .containsExactlyInAnyOrder(
                        Transaction.TransactionReason.ORDER_EARNING,
                        Transaction.TransactionReason.DELIVERY_FEE,
                        Transaction.TransactionReason.COD_SETTLEMENT,
                        Transaction.TransactionReason.PLATFORM_COMMISSION);
        assertThat(balanceRepository.findByEntityIdAndEntityType(22L, EntityType.SHIPPER)).get()
                .satisfies(balance -> {
                    assertThat(balance.getDepositBalance()).isEqualByComparingTo("0");
                    assertThat(balance.getTotalCodCollected()).isEqualByComparingTo("120000");
                });
    }

    private void startPrimaryReplica() {
        startListenersFor(primaryListenerRegistry);
    }

    private ConfigurableApplicationContext startReplica(String groupId) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SettlementServiceApplication.class)
                .web(WebApplicationType.NONE)
                .properties(replicaProperties(groupId))
                // The module's test resource deliberately leaves this value
                // blank to suppress incidental brokers. A command-line source
                // is required here so the independently booted replica cannot
                // be overridden by that safety default.
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
                Map.entry("app.kafka.topics.delivery-completed", TOPIC),
                Map.entry("spring.task.scheduling.enabled", "false"),
                Map.entry("spring.cloud.discovery.enabled", "false"),
                Map.entry("eureka.client.enabled", "false"),
                Map.entry("app.refund.processing-enabled", "false"),
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
                ConsumerConfig.GROUP_ID_CONFIG, "settlement-inbox-dlt-proof-" + UUID.randomUUID(),
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

    private String payload(UUID eventId, long orderId, String restaurantName) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"DELIVERY_COMPLETED\""
                + ",\"deliveryId\":970001,\"orderId\":" + orderId
                + ",\"restaurantId\":11,\"shipperId\":22"
                + ",\"restaurantName\":\"" + restaurantName + "\""
                + ",\"restaurantEarnings\":80000,\"restaurantCommission\":20000"
                + ",\"shippingFee\":20000,\"totalPrice\":120000"
                + ",\"shipperEarnings\":17000,\"shippingCommission\":3000"
                + ",\"totalPlatformEarnings\":23000,\"paymentMethod\":\"COD\"}";
    }

    private void closeReplicas() {
        for (int index = replicas.size() - 1; index >= 0; index--) {
            replicas.get(index).close();
        }
        replicas.clear();
    }
}
