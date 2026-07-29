package com.delivery.tracking_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DeliveryTrackingAccessClient {

    private final RestTemplate restTemplate;
    private final String deliveryServiceUrl;
    private final String internalSecret;

    public DeliveryTrackingAccessClient(
            RestTemplate restTemplate,
            @Value("${delivery.service.url}") String deliveryServiceUrl,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.deliveryServiceUrl = deliveryServiceUrl;
        this.internalSecret = internalSecret;
    }

    public boolean canTrack(Long deliveryId, Long userId, String role, Long shipperId) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("Internal secret is not configured");
        }
        String url = UriComponentsBuilder.fromUriString(deliveryServiceUrl)
                .path("/api/deliveries/internal/{deliveryId}/tracking-access")
                .queryParam("userId", userId)
                .queryParam("role", role)
                .queryParam("shipperId", shipperId)
                .buildAndExpand(deliveryId)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", internalSecret);
        ResponseEntity<InternalBaseResponse<Boolean>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<InternalBaseResponse<Boolean>>() {
                });
        InternalBaseResponse<Boolean> envelope = response.getBody();
        if (envelope == null || envelope.status() != 1 || envelope.data() == null) {
            throw new IllegalStateException("Invalid delivery tracking access response");
        }
        return envelope.data();
    }
}
