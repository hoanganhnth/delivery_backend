package com.delivery.tracking_service.listener;

import com.delivery.tracking_service.TrackingServiceApplication;
import com.delivery.tracking_service.entity.LocationHistoryReceipt;
import com.delivery.tracking_service.repository.LocationHistoryReceiptRepository;
import com.delivery.tracking_service.repository.ShipperLocationHistoryRepository;
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
 * Rehearses Tracking's durable support-history ingress with two application
 * replicas. Duplicate partitions and same/fresh-group raw replay must retain
 * exactly one receipt/history point; contradictory event-id reuse is poison
 * and must land in Tracking's same-partition owner DLT.
 */
@SpringBootTest(classes = TrackingServiceApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "app.websocket.redis-fanout-listener-enabled=false",
        "delivery.service.url=http://delivery-service"
})
@Testcontainers(disabledWithoutDocker = true)
class LocationHistoryKafkaPostgresIntegrationTest {

    private static final String TOPIC = "shipper.location-updated.tracking-history-proof";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracking_history_kafka")
            .withUsername("tracking")
            .withPassword("tracking");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private LocationHistoryReceiptRepository receiptRepository;
    @Autowired private ShipperLocationHistoryRepository historyRepository;
    @Autowired @Qualifier("trackingRetryKafkaTemplate") private KafkaTemplate<String, String> rawKafka;

    private final List<ConfigurableApplicationContext> replicas = new ArrayList<>();

    @BeforeEach
    void prepareBoundary() {
        closeReplicas();
        historyRepository.deleteAll();
        receiptRepository.deleteAll();
    }

    @AfterEach
    void stopReplicas() {
        closeReplicas();
    }

    @Test
    void locationHistoryKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoTrackingReplicas()
            throws Exception {
        createRequiredTopics();
        UUID eventId = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        String payload = payload(eventId, timestamp, "WEBSOCKET");
        String contradictory = payload(eventId, timestamp, "REST");
        String replicaGroup = "tracking-history-replicas-" + UUID.randomUUID();
        String replayGroup = "tracking-history-replay-" + UUID.randomUUID();

        startReplica(replicaGroup);
        startReplica(replicaGroup);
        await("two Tracking replicas to own the two source partitions", () ->
                targetPartitionOwners(replicaGroup).equals(Set.of(0, 1)));

        rawKafka.send(TOPIC, 0, "42", payload).get(10, TimeUnit.SECONDS);
        rawKafka.send(TOPIC, 1, "42", payload).get(10, TimeUnit.SECONDS);
        await("one Tracking history effect before both source offsets", () ->
                hasOnePersistedHistory() && committedOffsetsAtLeast(replicaGroup, 1, 1));

        rawKafka.send(TOPIC, 0, "42", payload).get(10, TimeUnit.SECONDS);
        await("same-group exact replay to commit as a no-op", () ->
                committedOffsetsAtLeast(replicaGroup, 2, 1));
        assertOnePersistedHistory();

        closeReplicas();
        startReplica(replayGroup);
        await("fresh Tracking group to replay historical offsets as no-ops", () ->
                committedOffsetsAtLeast(replayGroup, 2, 1));
        assertOnePersistedHistory();

        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(TOPIC + ".tracking.DLT")) {
            rawKafka.send(TOPIC, 1, "42", contradictory).get(10, TimeUnit.SECONDS);
            ConsumerRecord<String, String> dlt = awaitRecord(dltConsumer, TOPIC + ".tracking.DLT",
                    "contradictory Tracking receipt reuse to reach the DLT");
            assertThat(dlt.partition()).isEqualTo(1);
            assertThat(dlt.value()).isEqualTo(contradictory);
            await("contradictory source offset to be recovered after DLT publication", () ->
                    committedOffsetsAtLeast(replayGroup, 2, 2));
        }
        assertOnePersistedHistory();
    }

    private boolean hasOnePersistedHistory() {
        try {
            assertOnePersistedHistory();
            return true;
        } catch (AssertionError ignored) {
            return false;
        }
    }

    private void assertOnePersistedHistory() {
        assertThat(receiptRepository.findAll()).singleElement().satisfies(receipt -> {
            assertThat(receipt.getOutcome()).isEqualTo(LocationHistoryReceipt.Outcome.PERSISTED);
            assertThat(receipt.getPayloadFingerprint()).hasSize(64);
        });
        assertThat(historyRepository.count()).isEqualTo(1);
    }

    private ConfigurableApplicationContext startReplica(String groupId) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TrackingServiceApplication.class)
                .web(WebApplicationType.NONE)
                .properties(replicaProperties(groupId))
                .run();
        listenerRegistry(context).getListenerContainers().stream()
                .filter(container -> listensTo(container, TOPIC))
                .forEach(MessageListenerContainer::start);
        replicas.add(context);
        return context;
    }

    private Map<String, Object> replicaProperties(String groupId) {
        Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("spring.main.banner-mode", "off");
        properties.put("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
        properties.put("spring.kafka.listener.auto-startup", "false");
        properties.put("spring.kafka.admin.auto-create", "false");
        properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        properties.put("spring.datasource.username", POSTGRES.getUsername());
        properties.put("spring.datasource.password", POSTGRES.getPassword());
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("spring.flyway.enabled", "true");
        properties.put("app.kafka.topics.shipper-location-updated", TOPIC);
        properties.put("app.kafka.groups.location-history", groupId);
        properties.put("app.kafka.retry.auto-create-topics", "false");
        properties.put("spring.task.scheduling.enabled", "false");
        properties.put("spring.cloud.discovery.enabled", "false");
        properties.put("eureka.client.enabled", "false");
        properties.put("app.websocket.redis-fanout-listener-enabled", "false");
        properties.put("delivery.service.url", "http://delivery-service");
        return properties;
    }

    private KafkaListenerEndpointRegistry listenerRegistry(ConfigurableApplicationContext context) {
        return context.getBean(KafkaListenerEndpointRegistry.class);
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
                    new NewTopic(TOPIC + ".tracking.DLT", 2, (short) 1))).all().get(10, TimeUnit.SECONDS);
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
                ConsumerConfig.GROUP_ID_CONFIG, "tracking-history-dlt-proof-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer,
                                                        String topic, String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                if (topic.equals(record.topic())) {
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

    private String payload(UUID eventId, long timestamp, String source) {
        return "{\"shipperId\":42,\"latitude\":10.77,\"longitude\":106.70,\"isOnline\":true,"
                + "\"timestamp\":" + timestamp + ",\"eventId\":\"" + eventId
                + "\",\"deliveryId\":100,\"accuracy\":4.25,\"speed\":8.5,\"heading\":180.0,"
                + "\"source\":\"" + source + "\"}";
    }

    private void closeReplicas() {
        for (int index = replicas.size() - 1; index >= 0; index--) {
            replicas.get(index).close();
        }
        replicas.clear();
    }
}
