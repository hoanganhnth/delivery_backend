package com.delivery.search_service.consumer;

import com.delivery.search_service.dto.EntitySyncEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Uses Elasticsearch's atomic scripted update for the per-entity monotonic
 * checkpoint. A repository read followed by save is not safe when two Search
 * replicas receive reordered records on different Kafka partitions.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchEntitySyncCheckpointStore implements EntitySyncCheckpointStore {

    private static final String INDEX = "entity_sync_checkpoint";
    private static final int MAX_CONFLICT_RETRIES = 4;
    private static final String CLAIM_SCRIPT = """
            if (ctx.op == 'create') {
              ctx._source.eventId = params.eventId;
              ctx._source.occurredAt = params.occurredAt;
              ctx._source.action = params.action;
              ctx._source.payloadFingerprint = params.payloadFingerprint;
            } else if (ctx._source.eventId == params.eventId) {
              // A legacy checkpoint can serialize the same LocalDateTime
              // differently (for example, :00 seconds). Java normalizes and
              // validates it, then upgrades its fingerprint with an
              // _seq_no/_primary_term compare-and-set below.
              ctx.op = 'none';
            } else {
              if (ctx._source.occurredAt == null) {
                ctx.op = 'none';
              } else {
                int ordering = ctx._source.occurredAt.compareTo(params.occurredAt);
                if (ordering > 0 || ordering == 0) {
                  ctx.op = 'none';
                } else {
                  ctx._source.eventId = params.eventId;
                  ctx._source.occurredAt = params.occurredAt;
                  ctx._source.action = params.action;
                  ctx._source.payloadFingerprint = params.payloadFingerprint;
                }
              }
            }
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public ClaimResult claim(EntitySyncEvent event, String payloadFingerprint) {
        String occurredAt = event.getOccurredAt().toString();
        String action = event.getAction().toUpperCase(Locale.ROOT);
        String checkpointId = event.getEntityType().toUpperCase(Locale.ROOT) + ":" + event.getEntityId();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("eventId", event.getEventId().toString());
        params.put("occurredAt", occurredAt);
        params.put("action", action);
        params.put("payloadFingerprint", payloadFingerprint);

        Map<String, Object> requestBody = Map.of(
                "scripted_upsert", true,
                "script", Map.of("lang", "painless", "source", CLAIM_SCRIPT, "params", params),
                "upsert", Map.of());
        Request request = new Request("POST", "/" + INDEX + "/_update/"
                + encodePathSegment(checkpointId));
        request.addParameter("_source", "true");
        for (int attempt = 0; attempt < MAX_CONFLICT_RETRIES; attempt++) {
            try {
                request.setEntity(new StringEntity(objectMapper.writeValueAsString(requestBody),
                        ContentType.APPLICATION_JSON));
                Response response = restClient.performRequest(request);
                JsonNode body = readBody(response.getEntity());
                String result = body.path("result").asText();
                if ("created".equals(result) || "updated".equals(result)) {
                    return ClaimResult.APPLY;
                }
                if (!"noop".equals(result)) {
                    throw new IllegalStateException("Unexpected entity-sync checkpoint result: " + result);
                }
                return classifyNoop(body, event, payloadFingerprint, checkpointId);
            } catch (LegacyCheckpointConflictException ignored) {
                if (attempt + 1 == MAX_CONFLICT_RETRIES) {
                    throw new IllegalStateException("Elasticsearch legacy checkpoint upgrade conflicted repeatedly");
                }
                // Another replica changed the legacy document after it was
                // validated. Re-read/claim it before deciding whether this is
                // still an exact replay or a conflicting identity reuse.
            } catch (ResponseException exception) {
                if (exception.getResponse().getStatusLine().getStatusCode() != 409
                        || attempt + 1 == MAX_CONFLICT_RETRIES) {
                    throw new IllegalStateException("Elasticsearch checkpoint claim failed", exception);
                }
                // Concurrent scripted upserts can both see a missing document.
                // Retry against the winner's checkpoint and classify it there.
            } catch (IOException exception) {
                throw new IllegalStateException("Elasticsearch checkpoint is unavailable", exception);
            }
        }
        throw new IllegalStateException("Unreachable checkpoint conflict retry state");
    }

    private ClaimResult classifyNoop(JsonNode body, EntitySyncEvent event, String fingerprint,
                                     String checkpointId) {
        JsonNode source = body.path("get").path("_source");
        if (source.isMissingNode() || source.isNull()) {
            throw new IllegalStateException("Checkpoint claim returned no source for " + checkpointId);
        }
        String eventId = source.path("eventId").asText(null);
        String occurredAt = source.path("occurredAt").asText(null);
        String action = source.path("action").asText(null);
        String storedFingerprint = source.path("payloadFingerprint").asText(null);
        String expectedOccurredAt = event.getOccurredAt().toString();
        String expectedAction = event.getAction().toUpperCase(Locale.ROOT);

        if (event.getEventId().toString().equals(eventId)) {
            if (!sameOccurredAt(expectedOccurredAt, occurredAt) || !expectedAction.equals(action)) {
                throw new IllegalArgumentException(
                        "entity-sync eventId replay has contradictory metadata for " + checkpointId);
            }
            if (storedFingerprint == null) {
                upgradeLegacyFingerprint(body, fingerprint, checkpointId);
                return ClaimResult.APPLY;
            }
            if (!fingerprint.equals(storedFingerprint)) {
                throw new IllegalArgumentException(
                        "entity-sync eventId replay has contradictory payload for " + checkpointId);
            }
            return ClaimResult.EXACT_REPLAY;
        }
        if (occurredAt == null) {
            throw new IllegalStateException("Checkpoint has no comparable occurredAt for " + checkpointId);
        }
        int ordering = compareOccurredAt(occurredAt, expectedOccurredAt);
        if (ordering > 0) {
            return ClaimResult.STALE;
        }
        if (ordering == 0) {
            throw new IllegalArgumentException(
                    "Conflicting entity-sync events share the same occurredAt for " + checkpointId);
        }
        throw new IllegalStateException("Checkpoint claim regressed for " + checkpointId);
    }

    private boolean sameOccurredAt(String expected, String stored) {
        return parseOccurredAt(expected).equals(parseOccurredAt(stored));
    }

    private int compareOccurredAt(String left, String right) {
        return parseOccurredAt(left).compareTo(parseOccurredAt(right));
    }

    private LocalDateTime parseOccurredAt(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException localFormat) {
            try {
                // Spring Data Elasticsearch mappings written before this
                // checkpoint store can expose a Date field with an explicit
                // offset (typically a trailing Z). Their producer used a
                // LocalDateTime, so normalize the textual representation to
                // the original local fields before comparing it to a retry.
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (RuntimeException offsetFormat) {
                offsetFormat.addSuppressed(localFormat);
                throw new IllegalStateException("Checkpoint has an invalid occurredAt value", offsetFormat);
            }
        }
    }

    private void upgradeLegacyFingerprint(JsonNode body, String fingerprint, String checkpointId) {
        long sequenceNumber = body.path("_seq_no").asLong(-1L);
        long primaryTerm = body.path("_primary_term").asLong(-1L);
        if (sequenceNumber < 0 || primaryTerm < 0) {
            throw new IllegalStateException("Legacy checkpoint has no optimistic-lock metadata for " + checkpointId);
        }
        Map<String, Object> requestBody = Map.of(
                "script", Map.of(
                        "lang", "painless",
                        "source", "ctx._source.payloadFingerprint = params.payloadFingerprint",
                        "params", Map.of("payloadFingerprint", fingerprint)));
        Request request = new Request("POST", "/" + INDEX + "/_update/" + encodePathSegment(checkpointId));
        request.addParameter("if_seq_no", Long.toString(sequenceNumber));
        request.addParameter("if_primary_term", Long.toString(primaryTerm));
        try {
            request.setEntity(new StringEntity(objectMapper.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));
            restClient.performRequest(request);
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 409) {
                throw new LegacyCheckpointConflictException();
            }
            throw new IllegalStateException("Elasticsearch legacy checkpoint upgrade failed", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch legacy checkpoint is unavailable", exception);
        }
    }

    private JsonNode readBody(HttpEntity entity) throws IOException {
        if (entity == null) {
            throw new IllegalStateException("Elasticsearch checkpoint response has no body");
        }
        return objectMapper.readTree(entity.getContent());
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static final class LegacyCheckpointConflictException extends RuntimeException {
    }
}
