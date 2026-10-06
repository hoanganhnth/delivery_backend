package com.delivery.search_service.consumer;

import com.delivery.search_service.dto.EntitySyncEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Search projection fence at its real Elasticsearch boundary. Two
 * Kafka partitions can deliver the same aggregate out of order, so the old
 * writer is deliberately released after the new document has already won.
 */
@Testcontainers(disabledWithoutDocker = true)
class ElasticsearchProjectionConcurrencyIntegrationTest {

    @Container
    static final GenericContainer<?> ELASTICSEARCH = new GenericContainer<>(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.17.10"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200);

    private static RestClient client;

    @AfterAll
    static void closeClient() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void newerProjectionWinsWhenTwoReplicaClaimsAndWritesRaceAcrossPartitions() throws Exception {
        RestClient restClient = client();
        ObjectMapper objectMapper = new ObjectMapper();
        EntitySyncCheckpointStore checkpoints = new ElasticsearchEntitySyncCheckpointStore(restClient, objectMapper);
        SearchProjectionWriter writer = new ElasticsearchSearchProjectionWriter(restClient, objectMapper);
        String entityId = "race-" + UUID.randomUUID();
        LocalDateTime base = LocalDateTime.of(2026, 8, 12, 10, 0, 0, 123_456_789);
        EntitySyncEvent older = event(entityId, base, "Older projection");
        EntitySyncEvent newer = event(entityId, base.plusNanos(1), "Newer projection");

        // Two replicas claim concurrently. Either ordering is valid, but the
        // checkpoint can never let the old writer become authoritative later.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<EntitySyncCheckpointStore.ClaimResult> oldClaim = executor.submit(
                    claimAfterBarrier(checkpoints, older, barrier));
            Future<EntitySyncCheckpointStore.ClaimResult> newClaim = executor.submit(
                    claimAfterBarrier(checkpoints, newer, barrier));
            EntitySyncCheckpointStore.ClaimResult olderResult = oldClaim.get();
            EntitySyncCheckpointStore.ClaimResult newerResult = newClaim.get();

            assertThat(newerResult).isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);
            if (olderResult == EntitySyncCheckpointStore.ClaimResult.APPLY) {
                // Model the dangerous schedule: old claimed before new, but its
                // document request is delayed until after the new write.
                writer.apply(newer);
                writer.apply(older);
            } else {
                writer.apply(newer);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(documentName(restClient, entityId, objectMapper)).isEqualTo("Newer projection");
    }

    @Test
    void exactReplayCanRepairAfterProjectionFailureButContradictoryReuseFailsClosed() {
        RestClient restClient = client();
        ObjectMapper objectMapper = new ObjectMapper();
        EntitySyncCheckpointStore checkpoints = new ElasticsearchEntitySyncCheckpointStore(restClient, objectMapper);
        String entityId = "replay-" + UUID.randomUUID();
        EntitySyncEvent original = event(entityId, LocalDateTime.of(2026, 8, 12, 10, 1), "Canonical");
        EntitySyncEvent contradictory = EntitySyncEvent.builder()
                .eventId(original.getEventId())
                .occurredAt(original.getOccurredAt())
                .entityType("RESTAURANT")
                .entityId(entityId)
                .action("UPDATE")
                .payload(Map.of("name", "Tampered"))
                .build();

        String fingerprint = fingerprint(objectMapper, original);
        assertThat(checkpoints.claim(original, fingerprint))
                .isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);
        assertThat(checkpoints.claim(original, fingerprint))
                .isEqualTo(EntitySyncCheckpointStore.ClaimResult.EXACT_REPLAY);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                checkpoints.claim(contradictory, fingerprint(objectMapper, contradictory)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exactReplayUpgradesLegacyCheckpointButChangedActionStillFailsClosed() throws Exception {
        RestClient restClient = client();
        ObjectMapper objectMapper = new ObjectMapper();
        EntitySyncCheckpointStore checkpoints = new ElasticsearchEntitySyncCheckpointStore(restClient, objectMapper);
        String entityId = "legacy-" + UUID.randomUUID();
        EntitySyncEvent original = event(entityId, LocalDateTime.of(2026, 8, 12, 10, 1), "Canonical");
        EntitySyncEvent changedAction = EntitySyncEvent.builder()
                .eventId(original.getEventId())
                .occurredAt(original.getOccurredAt())
                .entityType("RESTAURANT")
                .entityId(entityId)
                .action("CREATE")
                .payload(original.getPayload())
                .build();
        writeLegacyCheckpoint(restClient, objectMapper, original);

        assertThat(checkpoints.claim(original, fingerprint(objectMapper, original)))
                .isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                checkpoints.claim(changedAction, fingerprint(objectMapper, changedAction)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteTombstonePreventsAnAlreadyClaimedOlderUpsertFromResurrectingTheDocument() throws Exception {
        RestClient restClient = client();
        ObjectMapper objectMapper = new ObjectMapper();
        EntitySyncCheckpointStore checkpoints = new ElasticsearchEntitySyncCheckpointStore(restClient, objectMapper);
        SearchProjectionWriter writer = new ElasticsearchSearchProjectionWriter(restClient, objectMapper);
        String entityId = "delete-race-" + UUID.randomUUID();
        LocalDateTime base = LocalDateTime.of(2026, 8, 12, 10, 2, 0, 123_456_789);
        EntitySyncEvent olderUpsert = event(entityId, base, "Must not resurrect");
        EntitySyncEvent delete = EntitySyncEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(base.plusNanos(1))
                .entityType("RESTAURANT")
                .entityId(entityId)
                .action("DELETE")
                .build();

        // The old record can claim before a newer delete on another Kafka
        // partition. Its document write is intentionally delayed until after
        // the delete, the schedule that would otherwise resurrect the entity.
        assertThat(checkpoints.claim(olderUpsert, fingerprint(objectMapper, olderUpsert)))
                .isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);
        assertThat(checkpoints.claim(delete, fingerprint(objectMapper, delete)))
                .isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);
        writer.apply(delete);
        writer.apply(olderUpsert);

        assertThat(documentExists(restClient, entityId)).isFalse();
    }

    private Callable<EntitySyncCheckpointStore.ClaimResult> claimAfterBarrier(
            EntitySyncCheckpointStore checkpoints, EntitySyncEvent event, CyclicBarrier barrier) {
        return () -> {
            barrier.await();
            return checkpoints.claim(event, fingerprint(new ObjectMapper(), event));
        };
    }

    private RestClient client() {
        if (client == null) {
            client = RestClient.builder(new HttpHost(ELASTICSEARCH.getHost(), ELASTICSEARCH.getMappedPort(9200)))
                    .build();
        }
        return client;
    }

    private EntitySyncEvent event(String entityId, LocalDateTime occurredAt, String name) {
        return EntitySyncEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(occurredAt)
                .entityType("RESTAURANT")
                .entityId(entityId)
                .action("UPDATE")
                .payload(Map.of("name", name))
                .build();
    }

    private String documentName(RestClient restClient, String entityId, ObjectMapper objectMapper) throws Exception {
        Response response = restClient.performRequest(new Request("GET", "/restaurant/_doc/" + entityId));
        JsonNode body = objectMapper.readTree(response.getEntity().getContent());
        return body.path("_source").path("name").asText();
    }

    private boolean documentExists(RestClient restClient, String entityId) throws Exception {
        try {
            restClient.performRequest(new Request("GET", "/restaurant/_doc/" + entityId));
            return true;
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private void writeLegacyCheckpoint(RestClient restClient, ObjectMapper objectMapper,
                                       EntitySyncEvent event) throws Exception {
        Request request = new Request("PUT", "/entity_sync_checkpoint/_doc/RESTAURANT%3A" + event.getEntityId());
        request.setEntity(new org.apache.http.entity.StringEntity(objectMapper.writeValueAsString(Map.of(
                "eventId", event.getEventId().toString(),
                // Spring Data's old @Field(Date) serialization can retain
                // zero seconds and return the value with a UTC offset, while
                // LocalDateTime.toString() omits both.
                "occurredAt", event.getOccurredAt().withSecond(0).withNano(0).toString() + ":00.000Z",
                "action", event.getAction())), org.apache.http.entity.ContentType.APPLICATION_JSON));
        restClient.performRequest(request);
    }

    private String fingerprint(ObjectMapper objectMapper, EntitySyncEvent event) {
        try {
            Map<String, Object> canonical = new java.util.TreeMap<>();
            canonical.put("action", event.getAction());
            canonical.put("entityId", event.getEntityId());
            canonical.put("entityType", event.getEntityType());
            canonical.put("occurredAt", event.getOccurredAt().toString());
            canonical.put("payload", event.getPayload());
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(canonical)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
