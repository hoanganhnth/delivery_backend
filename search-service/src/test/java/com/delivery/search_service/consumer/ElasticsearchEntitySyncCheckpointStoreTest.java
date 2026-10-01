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
