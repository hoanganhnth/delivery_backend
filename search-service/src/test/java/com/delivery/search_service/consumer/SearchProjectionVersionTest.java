package com.delivery.search_service.consumer;

import com.delivery.search_service.dto.EntitySyncEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchProjectionVersionTest {

    @Test
    void usesAggregateVersionAsTheElasticsearchExternalVersionWhenPresent() throws Exception {
        RestClient restClient = mock(RestClient.class);
        when(restClient.performRequest(org.mockito.ArgumentMatchers.any(Request.class)))
                .thenReturn(mock(Response.class));
        ElasticsearchSearchProjectionWriter writer = new ElasticsearchSearchProjectionWriter(restClient, new ObjectMapper());
        EntitySyncEvent event = EntitySyncEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(LocalDateTime.of(2026, 9, 30, 10, 0))
                .aggregateVersion(42L)
                .entityType("RESTAURANT")
                .entityId("restaurant-42")
                .action("UPDATE")
                .payload(Map.of("name", "Versioned restaurant"))
                .build();

        writer.apply(event);

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(request.capture());
        assertThat(request.getValue().getParameters().get("version")).isEqualTo("42");
    }
    @Test
    void legacyNanosecondsReachTheRequestUnchanged() throws Exception {
        RestClient restClient = mock(RestClient.class);
        when(restClient.performRequest(org.mockito.ArgumentMatchers.any(Request.class)))
                .thenReturn(mock(Response.class));
        ElasticsearchSearchProjectionWriter writer = new ElasticsearchSearchProjectionWriter(restClient, new ObjectMapper());
        EntitySyncEvent event = EntitySyncEvent.builder().entityType("DISH").entityId("7")
                .action("DELETE").occurredAt(LocalDateTime.of(1970, 1, 1, 0, 0, 1, 123)).build();
        writer.apply(event);
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(request.capture());
        assertThat(request.getValue().getParameters()).containsEntry("version", "1000000123")
                .containsEntry("version_type", "external_gte");
        assertThat(request.getValue().getMethod()).isEqualTo("DELETE");
    }

    @Test
    void invalidVersionsKeepAdapterErrorsAndDoNotWrite() {
        RestClient restClient = mock(RestClient.class);
        ElasticsearchSearchProjectionWriter writer = new ElasticsearchSearchProjectionWriter(restClient, new ObjectMapper());
        for (LocalDateTime time : new LocalDateTime[]{LocalDateTime.of(1970, 1, 1, 0, 0),
                LocalDateTime.of(1969, 12, 31, 23, 59, 59), LocalDateTime.MAX}) {
            EntitySyncEvent event = EntitySyncEvent.builder().entityType("DISH").entityId("7")
                    .action("DELETE").occurredAt(time).build();
            String expected = time.equals(LocalDateTime.MAX)
                    ? "entity-sync occurredAt cannot be represented as a version"
                    : "entity-sync occurredAt must be after the Unix epoch";
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.apply(event))
                    .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage(expected);
        }
        EntitySyncEvent unknown = EntitySyncEvent.builder().entityType("SHIPPER").build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.apply(unknown))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("Unsupported entity type: SHIPPER");
        org.mockito.Mockito.verifyNoInteractions(restClient);
    }

}
