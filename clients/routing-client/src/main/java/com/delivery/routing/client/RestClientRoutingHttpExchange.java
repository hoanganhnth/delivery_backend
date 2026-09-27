package com.delivery.routing.client;

import java.net.URI;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Spring Web adapter for the small transport capability required by RoutingClient. */
public final class RestClientRoutingHttpExchange implements RoutingHttpExchange {

    private final RestClient restClient;

    public RestClientRoutingHttpExchange(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
    }

    @Override
    public <T> ResponseEntity<T> post(
            URI uri, HttpHeaders headers, Object request, Class<T> responseType) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(responseType, "responseType");
        return restClient.post()
                .uri(uri)
                .headers(targetHeaders -> targetHeaders.addAll(headers))
                .body(request)
                .retrieve()
                .toEntity(responseType);
    }
}
