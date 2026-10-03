package com.delivery.tracking_service.listener;

import com.delivery.tracking_service.TrackingServiceApplication;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
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
import org.testcontainers.containers.GenericContainer;
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
 * Rehearses Tracking's separate delivery-room routing projection on the real
 * Kafka + Redis boundary. Two replica contexts share one group; exact replay
 * is harmless, while a contradictory same-timestamp BUSY fact is poison and
 * must not overwrite the room assignment before it reaches the owner DLT.
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
class ShipperDeliveryRoomKafkaRedisIntegrationTest {

    private static final String TOPIC = "shipper.status-change.tracking-routing-proof";
    private static final long SHIPPER_ID = 42L;
    private static final long ORDER_ID = 100L;
    private static final long DELIVERY_ID = 200L;

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracking_routing_kafka")
            .withUsername("tracking")
            .withPassword("tracking");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private ShipperDeliveryAssignmentStore assignments;
    @Autowired @Qualifier("trackingRetryKafkaTemplate") private KafkaTemplate<String, String> rawKafka;
    @Autowired private org.springframework.data.redis.core.StringRedisTemplate redis;

    private final List<ConfigurableApplicationContext> replicas = new ArrayList<>();

    @BeforeEach
    void prepareBoundary() {
        closeReplicas();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @AfterEach
    void stopReplicas() {
        closeReplicas();
    }

    @Test
    void routingReplayAndContradictorySameTimestampConvergeAcrossTwoTrackingReplicas() throws Exception {
        createRequiredTopics();
        UUID eventId = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        String payload = busy(eventId, DELIVERY_ID, timestamp);
        String contradictory = busy(UUID.randomUUID(), DELIVERY_ID + 1, timestamp);
        String replicaGroup = "tracking-routing-replicas-" + UUID.randomUUID();
        String replayGroup = "tracking-routing-replay-" + UUID.randomUUID();

        startReplica(replicaGroup);
        startReplica(replicaGroup);
        await("two Tracking routing replicas to own both source partitions", () ->
                targetPartitionOwners(replicaGroup).equals(Set.of(0, 1)));

        rawKafka.send(TOPIC, 0, Long.toString(SHIPPER_ID), payload).get(10, TimeUnit.SECONDS);
        rawKafka.send(TOPIC, 1, Long.toString(SHIPPER_ID), payload).get(10, TimeUnit.SECONDS);
        await("one Redis routing assignment before both source offsets", () ->
                assignments.activeDelivery(SHIPPER_ID).equals(java.util.Optional.of(DELIVERY_ID))
                        && committedOffsetsAtLeast(replicaGroup, 1, 1));

        rawKafka.send(TOPIC, 0, Long.toString(SHIPPER_ID), payload).get(10, TimeUnit.SECONDS);
        await("same-group routing replay to commit as a no-op", () ->
                committedOffsetsAtLeast(replicaGroup, 2, 1));
        assertThat(assignments.activeDelivery(SHIPPER_ID)).contains(DELIVERY_ID);

        closeReplicas();
        startReplica(replayGroup);
        await("fresh routing group to replay historical offsets as no-ops", () ->
                committedOffsetsAtLeast(replayGroup, 2, 1));
        assertThat(assignments.activeDelivery(SHIPPER_ID)).contains(DELIVERY_ID);

        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(TOPIC + ".tracking.DLT")) {
            rawKafka.send(TOPIC, 1, Long.toString(SHIPPER_ID), contradictory).get(10, TimeUnit.SECONDS);
            ConsumerRecord<String, String> dlt = awaitRecord(dltConsumer, TOPIC + ".tracking.DLT",
                    "contradictory routing fact to reach Tracking's owner DLT");
            assertThat(dlt.partition()).isEqualTo(1);
            assertThat(dlt.value()).isEqualTo(contradictory);
            await("contradictory routing source offset to recover after DLT publication", () ->
                    committedOffsetsAtLeast(replayGroup, 2, 2));
        }
        assertThat(assignments.activeDelivery(SHIPPER_ID)).contains(DELIVERY_ID);
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
        properties.put("spring.data.redis.host", REDIS.getHost());
        properties.put("spring.data.redis.port", REDIS.getMappedPort(6379));
        properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        properties.put("spring.datasource.username", POSTGRES.getUsername());
        properties.put("spring.datasource.password", POSTGRES.getPassword());
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("spring.flyway.enabled", "true");
        properties.put("app.kafka.topics.shipper-status-change", TOPIC);
        properties.put("app.kafka.groups.delivery-rooms", groupId);
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
                ConsumerConfig.GROUP_ID_CONFIG, "tracking-routing-dlt-proof-" + UUID.randomUUID(),
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
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for " + description, exception);
            }
        }
        fail("Timed out waiting for " + description);
    }

    private String busy(UUID eventId, long deliveryId, long timestamp) {
        return "{\"eventId\":\"" + eventId + "\",\"shipperId\":" + SHIPPER_ID
                + ",\"deliveryId\":" + deliveryId + ",\"orderId\":" + ORDER_ID
                + ",\"timestamp\":" + timestamp + ",\"status\":\"BUSY\"}";
    }

    private void closeReplicas() {
        for (int index = replicas.size() - 1; index >= 0; index--) {
            replicas.get(index).close();
        }
        replicas.clear();
    }
}
