package com.delivery.shipper_service.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Synchronous command boundary from the Shipper profile projection to the
 * Tracking availability authority. A failed command deliberately prevents an
 * offline profile projection from being persisted.
 */
@Component
public class TrackingAvailabilityClient {

    private final RestTemplate restTemplate;
    private final String trackingServiceUrl;
    private final String internalSecret;

    public TrackingAvailabilityClient(
            @Qualifier("trackingAvailabilityRestTemplate") RestTemplate restTemplate,
            @Value("${app.shipper.tracking-service.url:http://tracking-service:8093}") String trackingServiceUrl,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.trackingServiceUrl = trackingServiceUrl;
        this.internalSecret = internalSecret;
    }

    public void markOffline(Long shipperId) {
        if (shipperId == null || shipperId <= 0) {
            throw new IllegalArgumentException("shipperId must be positive");
        }
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SECRET is required for Tracking availability convergence");
        }
        if (trackingServiceUrl == null || trackingServiceUrl.isBlank()) {
            throw new IllegalStateException("Tracking service URL is required for availability convergence");
        }

        String url = UriComponentsBuilder.fromUriString(trackingServiceUrl)
                .path("/api/tracking/internal/shippers/{shipperId}/offline")
                .buildAndExpand(shipperId)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", internalSecret);

        try {
            ResponseEntity<TrackingAvailabilityResponse<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            TrackingAvailabilityResponse<Void> body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()
                    || body == null
                    || body.status() != 1) {
                throw new IllegalStateException("Tracking rejected the offline availability command");
            }
        } catch (RestClientException failure) {
            throw new IllegalStateException("Tracking availability authority is unavailable", failure);
        }
    }
}

record TrackingAvailabilityResponse<T>(int status, T data, String message) {
}
