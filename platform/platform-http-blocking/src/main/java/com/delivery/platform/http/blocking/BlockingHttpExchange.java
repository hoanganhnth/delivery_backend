package com.delivery.platform.http.blocking;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Synchronous transport only. Callers own credentials, timeout construction,
 * retries, fallbacks and interpretation of service-specific responses.
 */
public interface BlockingHttpExchange {

    <T> ResponseEntity<T> get(URI uri, HttpHeaders headers, Class<T> responseType);
}
