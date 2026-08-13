package com.delivery.notification_service.listener;

import com.delivery.notification_service.NotificationServiceApplication;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.repository.NotificationRepository;
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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises every customer-visible Kafka-to-notification ingress. The PENDING
 * row is atomically claimed before best-effort FCM delivery, so duplicate
 * partitions and fresh groups converge to one customer-visible notification
 * while a contradictory event ID remains a poison record for the owner DLT.
 */
@SpringBootTest(classes = NotificationServiceApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class NotificationKafkaPostgresIntegrationTest {

    private static final String ORDER_TOPIC = "order.created.notification-inbox-proof";
    private static final String DELIVERY_STATUS_TOPIC = "delivery.status-updated.notification-inbox-proof";
    private static final String SHIPPER_OFFER_TOPIC = "delivery.shipper-offered.notification-inbox-proof";
    private static final String REPLICA_GROUP = "notification-inbox-replicas";
    private static final String REPLAY_GROUP = "notification-inbox-replay";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_kafka")
            .withUsername("notification")
            .withPassword("notification");

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
        registry.add("app.kafka.topics.order-created", () -> ORDER_TOPIC);
        registry.add("app.kafka.topics.delivery-status-updated", () -> DELIVERY_STATUS_TOPIC);
        registry.add("app.kafka.topics.shipper-offered", () -> SHIPPER_OFFER_TOPIC);
        registry.add("spring.kafka.consumer.group-id", () -> REPLICA_GROUP);
    }

    @Autowired private NotificationRepository notificationRepository;
    @Autowired @Qualifier("retryKafkaTemplate") private KafkaTemplate<String, String> rawKafka;
    @Autowired private KafkaListenerEndpointRegistry primaryListenerRegistry;

    private final List<ConfigurableApplicationContext> replicas = new ArrayList<>();

    @BeforeEach
    void prepareBoundary() throws Exception {
        closeReplicas();
        stopPrimaryListeners();
        notificationRepository.deleteAll();
        createRequiredTopics();
    }

    @AfterEach
    void stopReplicas() {
        closeReplicas();
        stopPrimaryListeners();
    }

    @Test
    void orderCreatedKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, "Restaurant A");
        rehearseReplayAndContradiction(
                ORDER_TOPIC,
                eventId.toString(),
                "970001",
                payload,
                payload(eventId, "Contradictory restaurant"),
                ignored -> assertOneOrderCreatedNotification(eventId));
    }

    @Test
    void deliveryStatusKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = deliveryStatusPayload(eventId, "DELIVERING", "Shipper A");

        rehearseReplayAndContradiction(
                DELIVERY_STATUS_TOPIC,
                eventId.toString(),
                "970002",
                payload,
                deliveryStatusPayload(eventId, "DELIVERED", "Shipper A"),
                ignored -> assertOneDeliveryStatusNotification(eventId));
    }

    @Test
    void shipperOfferKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas()
            throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = shipperOfferPayload(eventId, "Restaurant A");

        rehearseReplayAndContradiction(
                SHIPPER_OFFER_TOPIC,
                eventId,
                "970003",
                payload,
                shipperOfferPayload(eventId, "Contradictory restaurant"),
                ignored -> assertOneShipperOfferNotification(eventId));
    }

    private void assertOneOrderCreatedNotification(UUID eventId) {
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getDeduplicationKey()).isEqualTo("order-created:" + eventId);
            assertThat(notification.getStatus()).isEqualTo("SENT");
            assertThat(notification.getUserId()).isEqualTo(7L);
            assertThat(notification.getRelatedEntityId()).isEqualTo(970_001L);
        });
    }

    private void assertOneDeliveryStatusNotification(UUID eventId) {
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getDeduplicationKey()).isEqualTo("delivery-status:" + eventId);
            assertThat(notification.getStatus()).isEqualTo("SENT");
            assertThat(notification.getUserId()).isEqualTo(7L);
            assertThat(notification.getRelatedEntityId()).isEqualTo(970_002L);
        });
    }

    private void assertOneShipperOfferNotification(String eventId) {
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getDeduplicationKey()).isEqualTo("shipper-offer:" + eventId + ":8");
            assertThat(notification.getStatus()).isEqualTo("SENT");
            assertThat(notification.getUserId()).isEqualTo(8L);
            assertThat(notification.getRelatedEntityId()).isEqualTo(970_003L);
        });
    }

    private void rehearseReplayAndContradiction(String topic, String eventId, String key, String payload,
                                                String contradictory, Consumer<String> assertOneNotification)
            throws Exception {
        String dltTopic = topic + ".notification.DLT";
        startPrimaryReplica(topic);
        startReplica(REPLICA_GROUP, topic);
        await("two Notification replicas to own both " + topic + " source partitions", () ->
                targetPartitionOwners(REPLICA_GROUP, topic).equals(Set.of(0, 1)));

        rawKafka.send(topic, 0, key, payload).get(10, TimeUnit.SECONDS);
        rawKafka.send(topic, 1, key, payload).get(10, TimeUnit.SECONDS);

        await("one committed " + topic + " notification before both source offsets", () ->
                notificationRepository.count() == 1
                        && committedOffsetsAtLeast(REPLICA_GROUP, topic, 1, 1));
        assertOneNotification.accept(eventId);

        rawKafka.send(topic, 0, key, payload).get(10, TimeUnit.SECONDS);
        await("same-group exact " + topic + " replay to commit as a no-op", () ->
                committedOffsetsAtLeast(REPLICA_GROUP, topic, 2, 1));
        assertOneNotification.accept(eventId);

        closeReplicas();
        stopPrimaryListeners(topic);
        startReplica(REPLAY_GROUP, topic);
        await("fresh notification group to replay " + topic + " history as no-ops", () ->
                committedOffsetsAtLeast(REPLAY_GROUP, topic, 2, 1));
        assertOneNotification.accept(eventId);

        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(dltTopic)) {
            rawKafka.send(topic, 1, key, contradictory).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> dlt = awaitRecord(
                    dltConsumer, dltTopic, "contradictory " + topic + " reuse to reach the owner DLT");
            assertThat(dlt.value()).isEqualTo(contradictory);
            await("contradictory " + topic + " source offset to recover after DLT publication", () ->
                    committedOffsetsAtLeast(REPLAY_GROUP, topic, 2, 2));
        }
        assertOneNotification.accept(eventId);
    }

    private void startPrimaryReplica(String topic) {
        startListenersFor(primaryListenerRegistry, topic);
    }

    private ConfigurableApplicationContext startReplica(String groupId, String topic) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(NotificationServiceApplication.class)
                .web(WebApplicationType.NONE)
                .properties(replicaProperties(groupId))
                // Test resources deliberately avoid an incidental broker; this
                // command-line source retains the disposable broker here.
                .run("--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers());
        startListenersFor(context.getBean(KafkaListenerEndpointRegistry.class), topic);
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
                Map.entry("app.kafka.topics.order-created", ORDER_TOPIC),
                Map.entry("app.kafka.topics.delivery-status-updated", DELIVERY_STATUS_TOPIC),
                Map.entry("app.kafka.topics.shipper-offered", SHIPPER_OFFER_TOPIC),
                Map.entry("spring.task.scheduling.enabled", "false"),
                Map.entry("spring.cloud.discovery.enabled", "false"),
                Map.entry("eureka.client.enabled", "false"));
    }

    private void startListenersFor(KafkaListenerEndpointRegistry registry, String topic) {
        registry.getListenerContainers().stream()
                .filter(container -> listensTo(container, topic))
                .forEach(container -> {
                    if (!container.isRunning()) {
                        container.start();
                    }
                });
    }

    private void stopPrimaryListeners() {
        primaryListenerRegistry.getListenerContainers().stream()
                .filter(container -> listensToAnyBoundaryTopic(container))
                .forEach(container -> {
                    if (container.isRunning()) {
                        container.stop();
                    }
                });
    }

    private void stopPrimaryListeners(String topic) {
        primaryListenerRegistry.getListenerContainers().stream()
                .filter(container -> listensTo(container, topic))
                .forEach(container -> {
                    if (container.isRunning()) {
                        container.stop();
                    }
                });
    }

    private boolean listensToAnyBoundaryTopic(MessageListenerContainer container) {
        return listensTo(container, ORDER_TOPIC)
                || listensTo(container, DELIVERY_STATUS_TOPIC)
                || listensTo(container, SHIPPER_OFFER_TOPIC);
    }

    private boolean listensTo(MessageListenerContainer container, String topic) {
        String[] topics = container.getContainerProperties().getTopics();
        return topics != null && Arrays.asList(topics).contains(topic);
    }

    private Set<Integer> targetPartitionOwners(String groupId, String topic) {
        try (AdminClient admin = adminClient()) {
            ConsumerGroupDescription group = admin.describeConsumerGroups(List.of(groupId)).all()
                    .get(10, TimeUnit.SECONDS).get(groupId);
            return group.members().stream()
                    .flatMap(member -> member.assignment().topicPartitions().stream())
                    .filter(partition -> topic.equals(partition.topic()))
                    .map(TopicPartition::partition)
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private boolean committedOffsetsAtLeast(String groupId, String topic, long partitionZero, long partitionOne) {
        try (AdminClient admin = adminClient()) {
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets =
                    admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                            .get(10, TimeUnit.SECONDS);
            return offsets.getOrDefault(new TopicPartition(topic, 0),
                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0)).offset() >= partitionZero
                    && offsets.getOrDefault(new TopicPartition(topic, 1),
                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0)).offset() >= partitionOne;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void createRequiredTopics() throws Exception {
        try (AdminClient admin = adminClient()) {
            admin.createTopics(List.of(
                    new NewTopic(ORDER_TOPIC, 2, (short) 1),
                    new NewTopic(ORDER_TOPIC + ".notification.DLT", 2, (short) 1),
                    new NewTopic(DELIVERY_STATUS_TOPIC, 2, (short) 1),
                    new NewTopic(DELIVERY_STATUS_TOPIC + ".notification.DLT", 2, (short) 1),
                    new NewTopic(SHIPPER_OFFER_TOPIC, 2, (short) 1),
                    new NewTopic(SHIPPER_OFFER_TOPIC + ".notification.DLT", 2, (short) 1))).all()
                    .get(10, TimeUnit.SECONDS);
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
                ConsumerConfig.GROUP_ID_CONFIG, "notification-inbox-dlt-proof-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer, String topic,
                                                         String description) {
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

    private String payload(UUID eventId, String restaurantName) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":970001,\"userId\":7"
                + ",\"restaurantName\":\"" + restaurantName + "\"}";
    }

    private String deliveryStatusPayload(UUID eventId, String status, String shipperName) {
        return "{\"eventId\":\"" + eventId + "\",\"deliveryId\":970002,\"orderId\":960002,\"userId\":7"
                + ",\"status\":\"" + status + "\",\"shipperName\":\"" + shipperName + "\"}";
    }

    private String shipperOfferPayload(String eventId, String restaurantName) {
        return "{\"eventId\":\"" + eventId + "\",\"deliveryId\":970003,\"orderId\":970003"
                + ",\"availableShippers\":[{\"shipperId\":8,\"distanceKm\":1.5}]"
                + ",\"restaurantName\":\"" + restaurantName + "\",\"pickupAddress\":\"Pickup A\""
                + ",\"deliveryAddress\":\"Delivery A\"}";
    }

    private void closeReplicas() {
        for (int index = replicas.size() - 1; index >= 0; index--) {
            replicas.get(index).close();
        }
        replicas.clear();
    }
}
