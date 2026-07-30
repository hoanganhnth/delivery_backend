package com.delivery.restaurant_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.delivery.restaurant_service.config.RestaurantOrderCircuitBreaker;

@Component
public class OrderDecisionEligibilityClient {

    private final RestTemplate restTemplate;
    private final String orderServiceUrl;
    private final String internalSecret;
    private final RestaurantOrderCircuitBreaker circuitBreaker;

    public OrderDecisionEligibilityClient(
            RestTemplate restTemplate,
            @Value("${order.service.url}") String orderServiceUrl,
            @Value("${app.internal.secret:}") String internalSecret,
            RestaurantOrderCircuitBreaker circuitBreaker) {
        this.restTemplate = restTemplate;
        this.orderServiceUrl = orderServiceUrl;
        this.internalSecret = internalSecret;
        this.circuitBreaker = circuitBreaker;
    }

    public void requirePendingOrderForRestaurant(Long orderId, Long restaurantId) {
        if (orderId == null || restaurantId == null) {
            throw new IllegalArgumentException("orderId and restaurantId are required");
        }
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SECRET is required for order decision validation");
        }

        String url = UriComponentsBuilder.fromUriString(orderServiceUrl)
                .path("/api/orders/internal/{orderId}/restaurant-decision-eligibility")
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
            throw new IllegalArgumentException("Order is not pending for this restaurant");
        }
    }
}
