package com.delivery.search_service.consumer;

import com.delivery.search.contracts.EntitySyncEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ElasticsearchEntitySyncCheckpointStoreTest {
    private final RestClient client = mock(RestClient.class);
    private final ElasticsearchEntitySyncCheckpointStore store =
            new ElasticsearchEntitySyncCheckpointStore(client, new ObjectMapper());
    private final EntitySyncEvent event = event();

    @Test
    void newCheckpointAppliesAndTransportFailureFailsClosed() throws Exception {
        Response created = response("{\"result\":\"created\"}");
        when(client.performRequest(any(Request.class))).thenReturn(created)
                .thenThrow(new IOException("Elasticsearch unavailable"));

        assertThat(store.claim(event, "fingerprint")).isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);
        assertThatThrownBy(() -> store.claim(event, "fingerprint"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void matchingNoopIsExactReplayButReusedIdWithDifferentPayloadIsRejected() throws Exception {
        String source = source(event.getEventId().toString(), event.getOccurredAt().toString(),
                "UPDATE", "original");
        Response replay = response(noop(source));
        when(client.performRequest(any(Request.class))).thenReturn(replay);

        assertThat(store.claim(event, "original")).isEqualTo(EntitySyncCheckpointStore.ClaimResult.EXACT_REPLAY);
        assertThatThrownBy(() -> store.claim(event, "different"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradictory payload");
    }

    @Test
    void olderEventIsStaleAndSameTimestampWithDifferentIdIsConflict() throws Exception {
        String later = source(UUID.randomUUID().toString(), event.getOccurredAt().plusSeconds(1).toString(),
                "UPDATE", "later");
        String sameTime = source(UUID.randomUUID().toString(), event.getOccurredAt().toString(),
                "UPDATE", "other");
        Response stale = response(noop(later));
        Response conflict = response(noop(sameTime));
        when(client.performRequest(any(Request.class)))
                .thenReturn(stale, conflict);

        assertThat(store.claim(event, "original")).isEqualTo(EntitySyncCheckpointStore.ClaimResult.STALE);
        assertThatThrownBy(() -> store.claim(event, "original"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same occurredAt");
    }

    @Test
    void legacyReplayWithEquivalentOffsetTimeUpgradesFingerprintByCompareAndSet() throws Exception {
        String legacy = source(event.getEventId().toString(), "2026-08-12T10:00:00.000Z",
                "UPDATE", null);
        Response replay = response("{\"result\":\"noop\",\"_seq_no\":3,\"_primary_term\":2,\"get\":{\"_source\":"
                + legacy + "}}");
        Response upgraded = response("{\"result\":\"updated\"}");
        when(client.performRequest(any(Request.class)))
                .thenReturn(replay, upgraded);

        assertThat(store.claim(event, "new-fingerprint")).isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);

        var requests = org.mockito.ArgumentCaptor.forClass(Request.class);
        verify(client, times(2)).performRequest(requests.capture());
        assertThat(requests.getAllValues().get(1).getParameters())
                .containsEntry("if_seq_no", "3").containsEntry("if_primary_term", "2");
    }


    @Test
    void createdAndUpdatedKeepScriptRequestAndEncodedEntityKey() throws Exception {
        event.setEntityType("dish"); event.setEntityId("a b/1"); event.setAction("update");
        Response updated = response("{\"result\":\"updated\"}");
        when(client.performRequest(any(Request.class))).thenReturn(updated);
        assertThat(store.claim(event, "fp")).isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);
        var requests = org.mockito.ArgumentCaptor.forClass(Request.class);
        verify(client).performRequest(requests.capture());
        Request request = requests.getValue();
        assertThat(request.getEndpoint()).isEqualTo("/entity_sync_checkpoint/_update/DISH%3Aa%20b%2F1");
        assertThat(request.getParameters()).containsEntry("_source", "true");
        var body = new ObjectMapper().readTree(org.apache.http.util.EntityUtils.toString(request.getEntity()));
        assertThat(body.path("scripted_upsert").asBoolean()).isTrue();
        assertThat(body.path("script").path("params").path("action").asText()).isEqualTo("UPDATE");
        assertThat(body.path("script").path("source").asText())
                .contains("ctx._source.occurredAt.compareTo(params.occurredAt)");
    }

    @Test
    void corruptNoopAndUnexpectedResponseFailClosed() throws Exception {
        for (String json : new String[]{"{\"result\":\"unknown\"}", "{\"result\":\"noop\"}",
                noop("null"), noop("{}"), noop(source("other", "2026-08-12T09:00", "UPDATE", "fp")),
                noop(source(event.getEventId().toString(), "bad", "UPDATE", "fp")),
                noop(source(event.getEventId().toString(), event.getOccurredAt().toString(), "DELETE", "fp")),
                noop(source(event.getEventId().toString(), event.getOccurredAt().toString(), "UPDATE", null))}) {
            Response response = response(json);
            when(client.performRequest(any(Request.class))).thenReturn(response);
            assertThatThrownBy(() -> store.claim(event, "fp")).isInstanceOf(RuntimeException.class);
        }
        Response noBody = mock(Response.class);
        when(client.performRequest(any(Request.class))).thenReturn(noBody);
        assertThatThrownBy(() -> store.claim(event, "fp")).hasMessage("Elasticsearch checkpoint response has no body");
    }

    @Test
    void claimConflictsRetryAtMostFourTimesAndOtherStatusesDoNotRetry() throws Exception {
        var conflict = failure(409);
        when(client.performRequest(any(Request.class))).thenThrow(conflict);
        assertThatThrownBy(() -> store.claim(event, "fp"))
                .hasMessage("Elasticsearch checkpoint claim failed").hasCause(conflict);
        verify(client, times(4)).performRequest(any(Request.class));
        reset(client);
        var unavailable = failure(503);
        when(client.performRequest(any(Request.class))).thenThrow(unavailable);
        assertThatThrownBy(() -> store.claim(event, "fp"))
                .hasMessage("Elasticsearch checkpoint claim failed").hasCause(unavailable);
        verify(client).performRequest(any(Request.class));
        reset(client);
        when(client.performRequest(any(Request.class))).thenThrow(conflict)
                .thenReturn(response("{\"result\":\"created\"}"));
        assertThat(store.claim(event, "fp")).isEqualTo(EntitySyncCheckpointStore.ClaimResult.APPLY);
        verify(client, times(2)).performRequest(any(Request.class));
    }

    @Test
    void legacyCasConflictReclaimsAndReclassifiesBeforeWrite() throws Exception {
        Response legacy = legacy();
        Response exact = response(noop(source(event.getEventId().toString(), event.getOccurredAt().toString(), "UPDATE", "fp")));
        when(client.performRequest(any(Request.class))).thenReturn(legacy).thenThrow(failure(409)).thenReturn(exact);
        assertThat(store.claim(event, "fp")).isEqualTo(EntitySyncCheckpointStore.ClaimResult.EXACT_REPLAY);
        verify(client, times(3)).performRequest(any(Request.class));
    }

    @Test
    void legacyCasConflictsAreBoundedAndTransportErrorsKeepExactMessages() throws Exception {
        Response legacy = legacy();
        when(client.performRequest(any(Request.class))).thenReturn(legacy).thenThrow(failure(409))
                .thenReturn(legacy).thenThrow(failure(409)).thenReturn(legacy).thenThrow(failure(409))
                .thenReturn(legacy).thenThrow(failure(409));
        assertThatThrownBy(() -> store.claim(event, "fp"))
                .hasMessage("Elasticsearch legacy checkpoint upgrade conflicted repeatedly");
        verify(client, times(8)).performRequest(any(Request.class));
        for (Exception failure : new Exception[]{failure(503), new IOException("offline")}) {
            reset(client);
            when(client.performRequest(any(Request.class))).thenReturn(legacy).thenThrow(failure);
            assertThatThrownBy(() -> store.claim(event, "fp"))
                    .hasMessage(failure instanceof IOException && !(failure instanceof org.elasticsearch.client.ResponseException)
                            ? "Elasticsearch legacy checkpoint is unavailable" : "Elasticsearch legacy checkpoint upgrade failed")
                    .hasCause(failure);
            verify(client, times(2)).performRequest(any(Request.class));
        }
    }

    @Test
    void rawTimestampDefectStillRejectsNewerFractionAfterScriptNoop() throws Exception {
        event.setOccurredAt(LocalDateTime.of(2026, 8, 12, 10, 0, 0, 1));
        Response regressed = response(noop(source("other", "2026-08-12T10:00:00Z", "UPDATE", "fp")));
        when(client.performRequest(any(Request.class))).thenReturn(regressed);
        assertThatThrownBy(() -> store.claim(event, "fp"))
                .hasMessage("Checkpoint claim regressed for RESTAURANT:42");
    }

    @Test
    void checkpointClockStillIgnoresHigherAggregateVersion() throws Exception {
        event.setAggregateVersion(100L);
        Response stale = response(noop(source("other",
                event.getOccurredAt().plusSeconds(1).toString(), "UPDATE", "fp")));
        when(client.performRequest(any(Request.class))).thenReturn(stale);
        assertThat(store.claim(event, "fp")).isEqualTo(EntitySyncCheckpointStore.ClaimResult.STALE);
    }


    @Test
    void legacyUpgradeRequiresBothOptimisticLockFields() throws Exception {
        for (String lock : new String[]{"", "\"_seq_no\":-1,\"_primary_term\":2,",
                "\"_seq_no\":3,\"_primary_term\":-1,"}) {
            reset(client);
            Response legacy = response("{\"result\":\"noop\"," + lock + "\"get\":{\"_source\":"
                    + source(event.getEventId().toString(), event.getOccurredAt().toString(), "UPDATE", null) + "}}");
            when(client.performRequest(any(Request.class))).thenReturn(legacy);
            assertThatThrownBy(() -> store.claim(event, "fp"))
                    .hasMessage("Legacy checkpoint has no optimistic-lock metadata for RESTAURANT:42");
            verify(client).performRequest(any(Request.class));
        }
    }

    private Response legacy() {
        return response("{\"result\":\"noop\",\"_seq_no\":3,\"_primary_term\":2,\"get\":{\"_source\":"
                + source(event.getEventId().toString(), event.getOccurredAt().toString(), "UPDATE", null) + "}}");
    }

    private static org.elasticsearch.client.ResponseException failure(int status) throws IOException {
        Response response = mock(Response.class);
        when(response.getStatusLine()).thenReturn(new org.apache.http.message.BasicStatusLine(
                new org.apache.http.ProtocolVersion("HTTP", 1, 1), status, "failed"));
        when(response.getRequestLine()).thenReturn(new org.apache.http.message.BasicRequestLine("POST", "/test", new org.apache.http.ProtocolVersion("HTTP", 1, 1)));
        when(response.getHost()).thenReturn(new org.apache.http.HttpHost("localhost", 9200));
        return new org.elasticsearch.client.ResponseException(response);
    }

    private static EntitySyncEvent event() {
        EntitySyncEvent event = new EntitySyncEvent();
        event.setEventId(UUID.randomUUID());
        event.setEntityType("RESTAURANT");
        event.setEntityId("42");
        event.setAction("UPDATE");
        event.setOccurredAt(LocalDateTime.of(2026, 8, 12, 10, 0));
        return event;
    }

    private static Response response(String json) {
        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity(json, ContentType.APPLICATION_JSON));
        return response;
    }

    private static String source(String eventId, String occurredAt, String action, String fingerprint) {
        return "{\"eventId\":\"" + eventId + "\",\"occurredAt\":\"" + occurredAt
                + "\",\"action\":\"" + action + "\""
                + (fingerprint == null ? "" : ",\"payloadFingerprint\":\"" + fingerprint + "\"") + "}";
    }

    private static String noop(String source) {
        return "{\"result\":\"noop\",\"get\":{\"_source\":" + source + "}}";
    }
}
