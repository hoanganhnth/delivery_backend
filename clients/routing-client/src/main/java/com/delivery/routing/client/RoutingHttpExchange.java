package com.delivery.routing.client;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** Minimal blocking POST capability used by the typed routing client. */
public interface RoutingHttpExchange {

    <T> ResponseEntity<T> post(
            URI uri, HttpHeaders headers, Object request, Class<T> responseType);
}
