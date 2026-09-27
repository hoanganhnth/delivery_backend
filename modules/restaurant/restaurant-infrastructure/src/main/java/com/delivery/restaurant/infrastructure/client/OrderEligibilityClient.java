package com.delivery.restaurant.infrastructure.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** HTTP adapter for Order's delivered-order rating eligibility endpoint. */
public class OrderEligibilityClient implements OrderEligibilityPort {

    private final RestTemplate restTemplate;
    private final String orderServiceUrl;
    private final String internalSecret;
    private final RestaurantOrderCircuitBreaker circuitBreaker;

    public OrderEligibilityClient(
            RestTemplate restTemplate,
            String orderServiceUrl,
            String internalSecret,
            RestaurantOrderCircuitBreaker circuitBreaker) {
        this.restTemplate = restTemplate;
        this.orderServiceUrl = orderServiceUrl;
        this.internalSecret = internalSecret;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
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
        InternalBaseResponse<Boolean> response = circuitBreaker.execute(() -> restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<InternalBaseResponse<Boolean>>() {
                }).getBody());
        if (response == null || response.status() != 1 || !Boolean.TRUE.equals(response.data())) {
            throw new IllegalArgumentException("Only the customer of a delivered order may rate this restaurant");
        }
    }
}
