package com.delivery.restaurant_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OrderEligibilityClient {

    private final RestTemplate restTemplate;
    private final String orderServiceUrl;
    private final String internalSecret;

    public OrderEligibilityClient(
            RestTemplate restTemplate,
            @Value("${order.service.url}") String orderServiceUrl,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.orderServiceUrl = orderServiceUrl;
        this.internalSecret = internalSecret;
    }

    public void requireDeliveredOrder(Long orderId, Long userId, Long restaurantId) {
        if (orderId == null || userId == null || restaurantId == null) {
            throw new IllegalArgumentException("Order, user and restaurant are required for rating");
        }
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SECRET is required for order eligibility validation");
        }

        String url = UriComponentsBuilder.fromUriString(orderServiceUrl)
                .path("/api/orders/internal/{orderId}/rating-eligibility")
                .queryParam("userId", userId)
                .queryParam("restaurantId", restaurantId)
                .buildAndExpand(orderId)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", internalSecret);
        InternalBaseResponse<Boolean> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<InternalBaseResponse<Boolean>>() {
                }).getBody();
        if (response == null || response.status() != 1 || !Boolean.TRUE.equals(response.data())) {
            throw new IllegalArgumentException("Only the customer of a delivered order may rate this restaurant");
        }
    }
}
