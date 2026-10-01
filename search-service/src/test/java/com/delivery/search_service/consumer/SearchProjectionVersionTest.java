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
}
