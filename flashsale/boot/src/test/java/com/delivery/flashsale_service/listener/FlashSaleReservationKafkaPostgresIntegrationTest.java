package com.delivery.flashsale_service.listener;

import com.delivery.flashsale_service.FlashsaleServiceApplication;
import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.entity.FlashSaleOutboxEvent;
import com.delivery.flashsale_service.entity.FlashSaleReservation;
import com.delivery.flashsale_service.repository.FlashSaleCampaignRepository;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleOrderReservationReceiptRepository;
import com.delivery.flashsale_service.repository.FlashSaleOutboxEventRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import com.delivery.flashsale_service.service.FlashSaleStockService;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
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
 * Exercises the default-off flash-sale capability at its real Kafka boundary.
 * Two replicas must converge to one receipt/COMMITTED reservation, and a
 * reused identity with different payload must be owner-DLT poison.
 */
@SpringBootTest(classes = FlashsaleServiceApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlashSaleReservationKafkaPostgresIntegrationTest {

    private static final String TOPIC = "order.created.flashsale-reservation-proof";
    private static final String DLT_TOPIC = TOPIC + ".flashsale.DLT";
    private static final String CANCELLED_TOPIC = "order.cancelled";
    private static final String CANCELLED_DLT_TOPIC = CANCELLED_TOPIC + ".flashsale.DLT";
    private static final String REFUND_TOPIC = "order.refund-eligible";
    private static final String REFUND_DLT_TOPIC = REFUND_TOPIC + ".flashsale.DLT";
    private static final String REPLICA_GROUP = "flashsale-reservation-replicas";
    private static final String REPLAY_GROUP = "flashsale-reservation-replay";
    private static final String CANCELLED_REPLAY_GROUP = "flashsale-reservation-cancelled-replay";
    private static final String REFUND_REPLAY_GROUP = "flashsale-reservation-refund-replay";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flashsale_kafka").withUsername("flashsale").withPassword("flashsale");

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
        registry.add("app.flashsale.checkout-enabled", () -> "true");
        registry.add("app.flashsale.outbox-relay-enabled", () -> "false");
        registry.add("app.flashsale.reservation-expiry-scan-ms", () -> "3600000");
        registry.add("app.kafka.topics.order-created", () -> TOPIC);
        registry.add("spring.kafka.consumer.group-id", () -> REPLICA_GROUP);
        registry.add("restaurant.service.url", () -> "http://127.0.0.1:1");
    }

    @Autowired private FlashSaleStockService stockService;
    @Autowired private FlashSaleOrderReservationReceiptRepository receiptRepository;
    @Autowired private FlashSaleOutboxEventRepository outboxRepository;
    @Autowired private FlashSaleReservationRepository reservationRepository;
    @Autowired private FlashSaleItemRepository itemRepository;
    @Autowired private FlashSaleCampaignRepository campaignRepository;
    @Autowired @Qualifier("retryKafkaTemplate") private KafkaTemplate<String, String> rawKafka;
    @Autowired private KafkaListenerEndpointRegistry primaryListenerRegistry;

    private final List<ConfigurableApplicationContext> replicas = new ArrayList<>();
    private UUID reservationId;

    @BeforeEach
    void prepareBoundary() throws Exception {
        closeReplicas();
        stopPrimaryListeners();
        receiptRepository.deleteAll();
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        itemRepository.deleteAll();
        campaignRepository.deleteAll();
        seedReservation();
        createRequiredTopics();
    }

    @AfterEach
    void stopReplicas() {
        closeReplicas();
        stopPrimaryListeners();
    }

    @Test
    @Order(1)
    void kafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoFlashSaleReplicas() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, 970001L, reservationId);

        startPrimaryReplica(TOPIC);
        startReplica(REPLICA_GROUP);
        await("two Flash-sale replicas to own both source partitions", () ->
                targetPartitionOwners(REPLICA_GROUP, TOPIC).equals(Set.of(0, 1)));

        rawKafka.send(TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        rawKafka.send(TOPIC, 1, "970001", payload).get(10, TimeUnit.SECONDS);
        await("one receipt and both source offsets", () -> receiptRepository.count() == 1
                && committedOffsetsAtLeast(REPLICA_GROUP, TOPIC, 1, 1));
        assertCommittedOnce(eventId);

        rawKafka.send(TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        await("same-group exact replay to commit as no-op", () ->
                committedOffsetsAtLeast(REPLICA_GROUP, TOPIC, 2, 1));
        assertCommittedOnce(eventId);

        closeReplicas();
        stopPrimaryListeners();
        startReplica(REPLAY_GROUP);
        await("fresh group to replay historical records as no-ops", () ->
                committedOffsetsAtLeast(REPLAY_GROUP, TOPIC, 2, 1));
        assertCommittedOnce(eventId);

        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(DLT_TOPIC)) {
            String contradictory = payload(eventId, 970002L, reservationId);
            rawKafka.send(TOPIC, 1, "970002", contradictory).get(10, TimeUnit.SECONDS);
            ConsumerRecord<String, String> dlt = awaitRecord(dltConsumer, DLT_TOPIC,
                    "contradictory flash-sale event identity to reach the owner DLT");
            assertThat(dlt.value()).isEqualTo(contradictory);
            assertThat(dlt.partition()).isEqualTo(1);
            await("contradictory source offset to recover after DLT publication", () ->
                    committedOffsetsAtLeast(REPLAY_GROUP, TOPIC, 2, 2));
        }
        assertCommittedOnce(eventId);
    }

    @Test
    @Order(2)
    void cancelledReleaseReplayAndContradictoryReuseConvergeAcrossTwoFlashSaleReplicas() throws Exception {
        stockService.commit(reservationId, 970001L);
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, 970001L, reservationId);

        startPrimaryReplica(CANCELLED_TOPIC);
        startReplica(REPLICA_GROUP);
        await("two Flash-sale replicas to own both cancelled partitions", () ->
                targetPartitionOwners(REPLICA_GROUP, CANCELLED_TOPIC).equals(Set.of(0, 1)));

        rawKafka.send(CANCELLED_TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        rawKafka.send(CANCELLED_TOPIC, 1, "970001", payload).get(10, TimeUnit.SECONDS);
        await("one cancelled receipt and both source offsets", () -> receiptRepository.count() == 1
                && committedOffsetsAtLeast(REPLICA_GROUP, CANCELLED_TOPIC, 1, 1));
        assertReleasedOnce(eventId);

        rawKafka.send(CANCELLED_TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        await("same-group cancelled replay to commit as no-op", () ->
                committedOffsetsAtLeast(REPLICA_GROUP, CANCELLED_TOPIC, 2, 1));
        assertReleasedOnce(eventId);

        closeReplicas();
        stopPrimaryListeners();
        startReplicaForSource(CANCELLED_REPLAY_GROUP, CANCELLED_TOPIC, Source.CANCELLED);
        await("fresh group to replay cancelled records as no-ops", () ->
                committedOffsetsAtLeast(CANCELLED_REPLAY_GROUP, CANCELLED_TOPIC, 2, 1));
        assertReleasedOnce(eventId);

        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(CANCELLED_DLT_TOPIC)) {
            String contradictory = payload(eventId, 970002L, reservationId);
            rawKafka.send(CANCELLED_TOPIC, 1, "970002", contradictory).get(10, TimeUnit.SECONDS);
            ConsumerRecord<String, String> dlt = awaitRecord(dltConsumer, CANCELLED_DLT_TOPIC,
                    "contradictory cancelled identity to reach the owner DLT");
            assertThat(dlt.value()).isEqualTo(contradictory);
            assertThat(dlt.partition()).isEqualTo(1);
            await("contradictory cancelled offset to recover after DLT publication", () ->
                    committedOffsetsAtLeast(CANCELLED_REPLAY_GROUP, CANCELLED_TOPIC, 2, 2));
        }
        assertReleasedOnce(eventId);
    }

    @Test
    @Order(3)
    void refundEligibleReleaseReplayAndContradictoryReuseConvergeAcrossTwoFlashSaleReplicas() throws Exception {
        stockService.commit(reservationId, 970001L);
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, 970001L, reservationId);

        startPrimaryReplica(REFUND_TOPIC);
        startReplica(REPLICA_GROUP);
        await("two Flash-sale replicas to own both refund partitions", () ->
                targetPartitionOwners(REPLICA_GROUP, REFUND_TOPIC).equals(Set.of(0, 1)));

        rawKafka.send(REFUND_TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        rawKafka.send(REFUND_TOPIC, 1, "970001", payload).get(10, TimeUnit.SECONDS);
        await("one refund receipt and both source offsets", () -> receiptRepository.count() == 1
                && committedOffsetsAtLeast(REPLICA_GROUP, REFUND_TOPIC, 1, 1));
        assertReleasedOnce(eventId);

        rawKafka.send(REFUND_TOPIC, 0, "970001", payload).get(10, TimeUnit.SECONDS);
        await("same-group refund replay to commit as no-op", () ->
                committedOffsetsAtLeast(REPLICA_GROUP, REFUND_TOPIC, 2, 1));
        assertReleasedOnce(eventId);

        closeReplicas();
        stopPrimaryListeners();
        startReplicaForSource(REFUND_REPLAY_GROUP, REFUND_TOPIC, Source.REFUND);
        await("fresh group to replay refund records as no-ops", () ->
                committedOffsetsAtLeast(REFUND_REPLAY_GROUP, REFUND_TOPIC, 2, 1));
        assertReleasedOnce(eventId);

        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(REFUND_DLT_TOPIC)) {
            String contradictory = payload(eventId, 970002L, reservationId);
            rawKafka.send(REFUND_TOPIC, 1, "970002", contradictory).get(10, TimeUnit.SECONDS);
            ConsumerRecord<String, String> dlt = awaitRecord(dltConsumer, REFUND_DLT_TOPIC,
                    "contradictory refund identity to reach the owner DLT");
            assertThat(dlt.value()).isEqualTo(contradictory);
            assertThat(dlt.partition()).isEqualTo(1);
            await("contradictory refund offset to recover after DLT publication", () ->
                    committedOffsetsAtLeast(REFUND_REPLAY_GROUP, REFUND_TOPIC, 2, 2));
        }
        assertReleasedOnce(eventId);
    }

    private void assertCommittedOnce(UUID eventId) {
        assertThat(receiptRepository.count()).isEqualTo(1);
        assertThat(receiptRepository.findById(eventId)).isPresent();
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getState())
                .isEqualTo(FlashSaleReservation.State.COMMITTED);
        assertThat(outboxRepository.findAll()).extracting(FlashSaleOutboxEvent::getEventType)
                .containsExactlyInAnyOrder("FLASH_SALE_RESERVATION_RESERVED", "FLASH_SALE_RESERVATION_COMMITTED");
    }

    private void assertReleasedOnce(UUID eventId) {
        assertThat(receiptRepository.count()).isEqualTo(1);
        assertThat(receiptRepository.findById(eventId)).isPresent();
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getState())
                .isEqualTo(FlashSaleReservation.State.RELEASED);
        assertThat(outboxRepository.findAll()).extracting(FlashSaleOutboxEvent::getEventType)
                .containsExactlyInAnyOrder("FLASH_SALE_RESERVATION_RESERVED", "FLASH_SALE_RESERVATION_COMMITTED",
                        "FLASH_SALE_RESERVATION_RELEASED");
    }

    private void seedReservation() {
        FlashSaleCampaign campaign = campaignRepository.saveAndFlush(FlashSaleCampaign.builder()
                .name("Kafka").isRecurring(false).startTime(LocalTime.MIN)
                .endTime(LocalTime.of(23, 59, 59))
                .status(FlashSaleCampaign.CampaignStatus.ACTIVE).adminId(1L).build());
        Long itemId = itemRepository.saveAndFlush(FlashSaleItem.builder().campaign(campaign)
                .restaurantId(9L).menuItemId(91L).originalPrice(new BigDecimal("100000"))
                .flashSalePrice(new BigDecimal("50000")).stockQuantity(10).soldQuantity(0)
                .status(FlashSaleItem.ItemStatus.APPROVED).build()).getId();
        reservationId = UUID.randomUUID();
        ReserveItemRequest line = new ReserveItemRequest();
        line.setFlashSaleItemId(itemId);
        line.setQuantity(1);
        FlashSaleReservationRequest request = new FlashSaleReservationRequest();
        request.setReservationId(reservationId);
        request.setOrderId(970001L);
        request.setUserId(7L);
        request.setRestaurantId(9L);
        request.setItems(List.of(line));
        stockService.reserveStock(request);
    }

    private void startPrimaryReplica(String topic) {
        startListenersFor(primaryListenerRegistry, topic);
    }

    private void startReplica(String groupId) {
        startReplica(groupId, TOPIC, CANCELLED_TOPIC, REFUND_TOPIC, TOPIC);
    }

    private void startReplicaForSource(String groupId, String topic, Source source) {
        String unused = "unused." + source.name().toLowerCase() + "." + UUID.randomUUID();
        String createdTopic = source == Source.CREATED ? topic : unused + ".created";
        String cancelledTopic = source == Source.CANCELLED ? topic : unused + ".cancelled";
        String refundTopic = source == Source.REFUND ? topic : unused + ".refund";
        startReplica(groupId, createdTopic, cancelledTopic, refundTopic, topic);
    }

    private void startReplica(String groupId, String createdTopic, String cancelledTopic,
                              String refundTopic, String listenerTopic) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(FlashsaleServiceApplication.class)
                .web(WebApplicationType.NONE)
                .properties(replicaProperties(groupId, createdTopic, cancelledTopic, refundTopic))
                .run("--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                        "--spring.kafka.consumer.group-id=" + groupId,
                        "--app.flashsale.checkout-enabled=true",
                        "--app.flashsale.outbox-relay-enabled=false",
                        "--app.kafka.topics.order-created=" + createdTopic,
                        "--app.kafka.topics.order-cancelled=" + cancelledTopic,
                        "--app.kafka.topics.refund-eligible=" + refundTopic,
                        "--spring.kafka.listener.auto-startup=false",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.flyway.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.flyway.user=" + POSTGRES.getUsername(),
                        "--spring.flyway.password=" + POSTGRES.getPassword());
        startListenersFor(context.getBean(KafkaListenerEndpointRegistry.class), listenerTopic);
        replicas.add(context);
    }

    private Map<String, Object> replicaProperties(String groupId, String createdTopic,
                                                   String cancelledTopic, String refundTopic) {
        return Map.ofEntries(
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers()),
                Map.entry("spring.kafka.consumer.group-id", groupId),
                Map.entry("spring.kafka.listener.auto-startup", "false"),
                Map.entry("spring.kafka.admin.auto-create", "false"),
                Map.entry("spring.datasource.url", POSTGRES.getJdbcUrl()),
                Map.entry("spring.datasource.username", POSTGRES.getUsername()),
                Map.entry("spring.datasource.password", POSTGRES.getPassword()),
                Map.entry("spring.flyway.user", POSTGRES.getUsername()),
                Map.entry("spring.flyway.password", POSTGRES.getPassword()),
                Map.entry("spring.datasource.driver-class-name", "org.postgresql.Driver"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                Map.entry("spring.flyway.enabled", "true"),
                Map.entry("spring.flyway.baseline-on-migrate", "true"),
                Map.entry("app.flashsale.checkout-enabled", "true"),
                Map.entry("app.flashsale.outbox-relay-enabled", "false"),
                Map.entry("app.flashsale.reservation-expiry-scan-ms", "3600000"),
                Map.entry("app.kafka.topics.order-created", createdTopic),
                Map.entry("app.kafka.topics.order-cancelled", cancelledTopic),
                Map.entry("app.kafka.topics.refund-eligible", refundTopic),
                Map.entry("spring.task.scheduling.enabled", "false"),
                Map.entry("spring.cloud.discovery.enabled", "false"),
                Map.entry("eureka.client.enabled", "false"),
                Map.entry("restaurant.service.url", "http://127.0.0.1:1"));
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
            admin.createTopics(List.of(new NewTopic(TOPIC, 2, (short) 1),
                    new NewTopic(DLT_TOPIC, 2, (short) 1),
                    new NewTopic(CANCELLED_TOPIC, 2, (short) 1),
                    new NewTopic(CANCELLED_DLT_TOPIC, 2, (short) 1),
                    new NewTopic(REFUND_TOPIC, 2, (short) 1),
                    new NewTopic(REFUND_DLT_TOPIC, 2, (short) 1))).all().get(10, TimeUnit.SECONDS);
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
                ConsumerConfig.GROUP_ID_CONFIG, "flashsale-reservation-dlt-proof-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer, String dltTopic,
                                                         String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                if (dltTopic.equals(record.topic())) {
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

    private String payload(UUID eventId, long orderId, UUID reservation) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":" + orderId
                + ",\"flashSaleReservationId\":\"" + reservation + "\"}";
    }

    private void closeReplicas() {
        for (int index = replicas.size() - 1; index >= 0; index--) {
            replicas.get(index).close();
        }
        replicas.clear();
    }

    private enum Source { CREATED, CANCELLED, REFUND }
}
