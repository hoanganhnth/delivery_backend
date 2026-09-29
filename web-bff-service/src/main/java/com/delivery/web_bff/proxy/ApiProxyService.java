package com.delivery.web_bff.proxy;

import com.delivery.web_bff.session.WebSessionService;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

public class ApiProxyService {
    private static final List<String> REQUEST_HEADERS = List.of(
            HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE, "Idempotency-Key", HttpHeaders.IF_MATCH,
            HttpHeaders.IF_NONE_MATCH, "X-Correlation-ID");
    private static final List<String> RESPONSE_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE, HttpHeaders.CACHE_CONTROL, HttpHeaders.ETAG,
            HttpHeaders.LAST_MODIFIED, HttpHeaders.LOCATION, "X-Correlation-ID", "Retry-After");
    private final RestClient gateway;
    private final WebSessionService sessions;

    public ApiProxyService(RestClient authRestClient, WebSessionService sessions) {
        this.gateway = authRestClient;
        this.sessions = sessions;
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, String path, String rawQuery,
            HttpHeaders browserHeaders, byte[] body, String rawSessionId, String csrfToken) {
        if (!ApiProxyPolicy.allows(method, path)) {
            throw new ApiProxyRejectedException("API path or method is not allowed");
        }
        boolean mutation = method != HttpMethod.GET && method != HttpMethod.HEAD && method != HttpMethod.OPTIONS;
        String bearer = sessions.accessToken(rawSessionId, csrfToken, mutation);
        String target = rawQuery == null || rawQuery.isBlank() ? path : path + "?" + rawQuery;

        return gateway.method(method).uri(URI.create(target))
                .headers(headers -> {
                    REQUEST_HEADERS.forEach(name -> {
                        List<String> values = browserHeaders.get(name);
                        if (values != null) headers.put(name, List.copyOf(values));
                    });
                    headers.setBearerAuth(bearer);
                    headers.remove(HttpHeaders.COOKIE);
                    headers.remove(HttpHeaders.HOST);
                })
                .body(body == null ? new byte[0] : body)
                .exchange((request, response) -> {
                    HttpHeaders safe = new HttpHeaders();
                    RESPONSE_HEADERS.forEach(name -> {
                        List<String> values = response.getHeaders().get(name);
                        if (values != null) safe.put(name, List.copyOf(values));
                    });
                    return new ResponseEntity<>(response.getBody().readAllBytes(), safe, response.getStatusCode());
                });
    }

    public static class ApiProxyRejectedException extends RuntimeException {
        public ApiProxyRejectedException(String message) { super(message); }
    }
}
