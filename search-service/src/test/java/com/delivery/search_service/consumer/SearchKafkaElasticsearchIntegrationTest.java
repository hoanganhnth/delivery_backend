package com.delivery.search_service.consumer;

import com.delivery.search_service.SearchServiceApplication;
import com.delivery.search_service.dto.EntitySyncEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
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
 * Exercises the full Restaurant-to-Search projection boundary. Two application
 * contexts emulate one Search consumer group on two partitions; fresh-group
 * replay and a contradictory event ID then prove the listener, checkpoint,
 * external-version document write and owner DLT converge together.
 */
@SpringBootTest(classes = SearchServiceApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "app.elasticsearch.enabled=true"
})
@Testcontainers(disabledWithoutDocker = true)
class SearchKafkaElasticsearchIntegrationTest {

    private static final String TOPIC = "entity-sync";
    private static final String DLT_TOPIC = TOPIC + ".DLT";
    private static final String REPLICA_GROUP = "search-sync-replicas";
    private static final String REPLAY_GROUP = "search-sync-replay";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final GenericContainer<?> ELASTICSEARCH = new GenericContainer<>(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.17.10"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> REPLICA_GROUP);
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHost()
                + ":" + ELASTICSEARCH.getMappedPort(9200));
        registry.add("spring.data.elasticsearch.repositories.enabled", () -> "true");
    }

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private KafkaListenerEndpointRegistry primaryListenerRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ConfigurableApplicationContext> replicas = new ArrayList<>();
    private RestClient restClient;

    @BeforeEach
    void prepareBoundary() throws Exception {
        closeReplicas();
        stopPrimaryListeners();
        createRequiredTopics();
    }

    @AfterEach
    void stopReplicas() {
        closeReplicas();
        stopPrimaryListeners();
    }

    @AfterAll
    static void releaseStaticResources() {
        // Testcontainers owns the broker and Elasticsearch lifecycle.
    }

    @Test
    void kafkaReplayReorderAndContradictoryReuseConvergeAcrossTwoSearchReplicas() throws Exception {
        String entityId = "search-kafka-" + UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 12, 11, 0, 0, 987_654_321);
        EntitySyncEvent older = event(UUID.randomUUID(), entityId, occurredAt, "Older restaurant");
        EntitySyncEvent newer = event(UUID.randomUUID(), entityId, occurredAt.plusNanos(1), "Newer restaurant");

        startPrimaryReplica();
        startReplica(REPLICA_GROUP);
        await("two Search replicas to own both entity-sync partitions", () ->
                targetPartitionOwners(REPLICA_GROUP).equals(Set.of(0, 1)));

        kafkaTemplate.send(TOPIC, 0, entityId, older).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(TOPIC, 1, entityId, newer).get(10, TimeUnit.SECONDS);
        await("newest Search projection after cross-partition reorder", () ->
                "Newer restaurant".equals(documentName(entityId))
                        && committedOffsetsAtLeast(REPLICA_GROUP, 1, 1));

        kafkaTemplate.send(TOPIC, 0, entityId, newer).get(10, TimeUnit.SECONDS);
        await("same-group exact replay to complete without regressing Search", () ->
                "Newer restaurant".equals(documentName(entityId))
                        && committedOffsetsAtLeast(REPLICA_GROUP, 2, 1));

        closeReplicas();
        stopPrimaryListeners();
        startReplica(REPLAY_GROUP);
        await("fresh Search group to own both entity-sync partitions", () ->
                targetPartitionOwners(REPLAY_GROUP).equals(Set.of(0, 1)));
        await("fresh Search group to replay historical entity-sync offsets", () ->
                committedOffsetsAtLeast(REPLAY_GROUP, 2, 1));
        assertThat(documentName(entityId)).isEqualTo("Newer restaurant");

        EntitySyncEvent contradictory = event(newer.getEventId(), entityId, newer.getOccurredAt(),
                "Contradictory restaurant");
        try (KafkaConsumer<String, String> dltConsumer = freshConsumer(DLT_TOPIC)) {
            kafkaTemplate.send(TOPIC, 1, entityId, contradictory).get(10, TimeUnit.SECONDS);
            ConsumerRecord<String, String> dlt = awaitRecord(dltConsumer,
                    "contradictory Search identity reuse to reach DLT");
            assertThat(objectMapper.readTree(dlt.value()).path("eventId").asText())
                    .isEqualTo(newer.getEventId().toString());
            await("contradictory Search source offset to recover after DLT publication", () ->
                    committedOffsetsAtLeast(REPLAY_GROUP, 2, 2));
        }
        assertThat(documentName(entityId)).isEqualTo("Newer restaurant");
    }

    private EntitySyncEvent event(UUID eventId, String entityId, LocalDateTime occurredAt, String name) {
        return EntitySyncEvent.builder()
                .eventId(eventId)
                .occurredAt(occurredAt)
                .entityType("RESTAURANT")
                .entityId(entityId)
                .action("UPDATE")
                .payload(Map.of("name", name))
                .build();
    }

    private void startPrimaryReplica() {
        startListenersFor(primaryListenerRegistry);
    }

    private void startReplica(String groupId) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SearchServiceApplication.class)
                .web(WebApplicationType.NONE)
                .properties(replicaProperties(groupId))
                .run("--spring.kafka.consumer.group-id=" + groupId,
                        "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                        "--app.elasticsearch.enabled=true",
                        "--spring.elasticsearch.uris=http://" + ELASTICSEARCH.getHost()
                                + ":" + ELASTICSEARCH.getMappedPort(9200));
        startListenersFor(context.getBean(KafkaListenerEndpointRegistry.class));
        replicas.add(context);
    }

    private Map<String, Object> replicaProperties(String groupId) {
        return Map.ofEntries(
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers()),
                Map.entry("spring.kafka.consumer.group-id", groupId),
                Map.entry("spring.kafka.listener.auto-startup", "false"),
                Map.entry("spring.kafka.admin.auto-create", "false"),
                Map.entry("spring.elasticsearch.uris", "http://" + ELASTICSEARCH.getHost()
                        + ":" + ELASTICSEARCH.getMappedPort(9200)),
                Map.entry("spring.data.elasticsearch.repositories.enabled", "true"),
                Map.entry("app.elasticsearch.enabled", "true"),
                Map.entry("spring.task.scheduling.enabled", "false"),
                Map.entry("spring.cloud.discovery.enabled", "false"),
                Map.entry("eureka.client.enabled", "false"));
    }

    private void startListenersFor(KafkaListenerEndpointRegistry registry) {
        List<MessageListenerContainer> containers = new ArrayList<>(registry.getListenerContainers());
        if (containers.size() != 1) {
            throw new IllegalStateException("Expected exactly one entity-sync listener, found " + containers.size()
                    + " from " + registry.getListenerContainerIds());
        }
        containers.forEach(container -> {
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
                ConsumerConfig.GROUP_ID_CONFIG, "search-dlt-proof-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer, String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
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

    private String documentName(String entityId) {
        try {
            if (restClient == null) {
                restClient = RestClient.builder(new HttpHost(ELASTICSEARCH.getHost(),
                        ELASTICSEARCH.getMappedPort(9200))).build();
            }
            JsonNode body = objectMapper.readTree(restClient.performRequest(
                    new Request("GET", "/restaurant/_doc/" + entityId)).getEntity().getContent());
            return body.path("_source").path("name").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void await(String description, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
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

    private void closeReplicas() {
        for (int index = replicas.size() - 1; index >= 0; index--) {
            replicas.get(index).close();
        }
        replicas.clear();
    }
}
