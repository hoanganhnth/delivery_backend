package com.delivery.promotion_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Promotion delegates restaurant ownership to Restaurant, the canonical
 * authority.  A transport or malformed response fails closed.
 */
@Service
public class RestaurantOwnershipClient {
    private final RestClient client;
    private final String internalSecret;

    public RestaurantOwnershipClient(
            @Value("${restaurant.service.url:http://restaurant-service:8082}") String restaurantUrl,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.client = RestClient.builder().baseUrl(restaurantUrl).build();
        this.internalSecret = internalSecret;
    }

    @SuppressWarnings("unchecked")
    public boolean isOwnedBy(Long restaurantId, Long ownerPrincipalId, Long legacyOwnerId) {
        if (restaurantId == null || restaurantId <= 0 || ownerPrincipalId == null || ownerPrincipalId <= 0
                || legacyOwnerId == null || legacyOwnerId <= 0) {
            return false;
        }
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("Internal restaurant credential is missing");
        }
        Map<String, Object> envelope = client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/restaurants/internal/{restaurantId}/owners/{ownerId}")
                        .queryParam("legacyOwnerId", legacyOwnerId)
                        .build(restaurantId, ownerPrincipalId))
                .header("Internal-Token", internalSecret)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        (request, response) -> { throw new IllegalStateException("Restaurant ownership check failed"); })
                .body(Map.class);
        if (envelope == null || !Integer.valueOf(1).equals(number(envelope.get("status")))) {
            throw new IllegalStateException("Restaurant ownership response is invalid");
        }
        return Boolean.TRUE.equals(envelope.get("data"));
    }

    private Integer number(Object value) {
        if (value instanceof Number number) return number.intValue();
        return value == null ? null : Integer.valueOf(value.toString());
    }
}
