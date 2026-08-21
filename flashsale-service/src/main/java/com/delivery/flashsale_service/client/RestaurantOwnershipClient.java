package com.delivery.flashsale_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RestaurantOwnershipClient {

    private final RestTemplate restTemplate;
    private final String restaurantServiceUrl;
    private final String internalSecret;

    public RestaurantOwnershipClient(
            RestTemplate restTemplate,
            @Value("${restaurant.service.url}") String restaurantServiceUrl,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.restaurantServiceUrl = restaurantServiceUrl;
        this.internalSecret = internalSecret;
    }

    public void requireOwnedBy(Long restaurantId, Long ownerPrincipalId, Long legacyOwnerId) {
        if (restaurantId == null || ownerPrincipalId == null || legacyOwnerId == null) {
            throw new IllegalArgumentException("Restaurant and owner identity are required");
        }
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SECRET is required for restaurant ownership validation");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", internalSecret);
        String url = restaurantServiceUrl + "/api/restaurants/internal/"
                + restaurantId + "/owners/" + ownerPrincipalId + "?legacyOwnerId=" + legacyOwnerId;
        InternalBaseResponse<Boolean> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<InternalBaseResponse<Boolean>>() {
                }).getBody();
        if (response == null || response.status() != 1 || !Boolean.TRUE.equals(response.data())) {
            throw new IllegalArgumentException("Merchant does not own restaurant " + restaurantId);
        }
    }

    /** Compatibility rail for pre-principal internal callers. */
    public void requireOwnedBy(Long restaurantId, Long legacyOwnerId) {
        if (restaurantId == null || legacyOwnerId == null) {
            throw new IllegalArgumentException("Restaurant and owner identity are required");
        }
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SECRET is required for restaurant ownership validation");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", internalSecret);
        String url = restaurantServiceUrl + "/api/restaurants/internal/"
                + restaurantId + "/owners/" + legacyOwnerId;
        InternalBaseResponse<Boolean> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<InternalBaseResponse<Boolean>>() {}).getBody();
        if (response == null || response.status() != 1 || !Boolean.TRUE.equals(response.data())) {
            throw new IllegalArgumentException("Merchant does not own restaurant " + restaurantId);
        }
    }
}
