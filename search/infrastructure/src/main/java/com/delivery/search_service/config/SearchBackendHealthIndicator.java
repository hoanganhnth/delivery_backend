package com.delivery.search_service.config;

import java.io.IOException;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Verifies that the configured Elasticsearch node can serve requests without
 * deserializing the cluster-health payload. The local Compose stack runs
 * Elasticsearch 7.17 while Spring Boot 3.5 supplies the 8.x Java client; the
 * stock contributor's typed cluster-health response is not backward
 * compatible, although repository queries are.
 */
@Component("searchBackend")
public class SearchBackendHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    public SearchBackendHealthIndicator(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Health health() {
        try {
            Response response = restClient.performRequest(new Request("HEAD", "/"));
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 400) {
                return Health.up().build();
            }
            return Health.down().withDetail("reason", "Elasticsearch is unavailable").build();
        } catch (IOException | RuntimeException exception) {
            return Health.down().withDetail("reason", "Elasticsearch is unavailable").build();
        }
    }
}
