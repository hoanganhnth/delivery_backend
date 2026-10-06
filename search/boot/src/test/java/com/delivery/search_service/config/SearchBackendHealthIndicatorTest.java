package com.delivery.search_service.config;

import java.io.IOException;

import org.apache.http.StatusLine;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchBackendHealthIndicatorTest {

    @Test
    void reportsUpWhenElasticsearchAcceptsTheLowLevelProbe() throws IOException {
        RestClient restClient = mock(RestClient.class);
        Response response = mock(Response.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(restClient.performRequest(any(Request.class))).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);

        Health health = new SearchBackendHealthIndicator(restClient).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(request.capture());
        assertThat(request.getValue().getMethod()).isEqualTo("HEAD");
        assertThat(request.getValue().getEndpoint()).isEqualTo("/");
    }

    @Test
    void reportsDownWithoutExposingTransportDetails() throws IOException {
        RestClient restClient = mock(RestClient.class);
        when(restClient.performRequest(any(Request.class))).thenThrow(new IOException("unreachable host"));

        Health health = new SearchBackendHealthIndicator(restClient).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "Elasticsearch is unavailable");
    }
}
