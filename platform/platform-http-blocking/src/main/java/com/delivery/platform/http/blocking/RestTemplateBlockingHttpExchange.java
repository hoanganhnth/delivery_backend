package com.delivery.platform.http.blocking;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public final class RestTemplateBlockingHttpExchange implements BlockingHttpExchange {

    private final RestTemplate restTemplate;

    public RestTemplateBlockingHttpExchange(
            RestTemplate restTemplate, HttpClient httpClient, Duration readTimeout) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
        Objects.requireNonNull(httpClient, "httpClient");
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("httpClient must disable redirects");
        }
        if (httpClient.connectTimeout().isEmpty()) {
            throw new IllegalArgumentException("httpClient connect timeout is required");
        }
        requirePositive(readTimeout, "readTimeout");
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restTemplate.setRequestFactory(requestFactory);
    }

    @Override
    public <T> ResponseEntity<T> get(URI uri, HttpHeaders headers, Class<T> responseType) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(responseType, "responseType");
        return restTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(null, headers), responseType);
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
