package com.delivery.restaurant_service.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.delivery.restaurant_service.config.OrderCallResilienceProperties;
import com.delivery.restaurant_service.config.RestaurantOrderCircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class OrderInternalClientsTest {

    @Mock
    private RestTemplate restTemplate;

    private OrderEligibilityClient ratingClient;
    private OrderDecisionEligibilityClient decisionClient;

    @BeforeEach
    void setUp() {
        ratingClient = new OrderEligibilityClient(
                restTemplate,
                "http://order-service",
                "test-secret", circuitBreaker());
        decisionClient = new OrderDecisionEligibilityClient(
                restTemplate,
                "http://order-service",
                "test-secret", circuitBreaker());
    }

    @Test
    void ratingEligibilityAcceptsCanonicalTrueEnvelope() {
        stub(new InternalBaseResponse<>(1, "Eligible", true));

        ratingClient.requireDeliveredOrder(10L, 20L, 30L);
    }

    @Test
    void ratingEligibilityRejectsFailureFalseNullAndEmptyEnvelopes() {
        assertRatingRejected(new InternalBaseResponse<>(0, "Denied", true));
        assertRatingRejected(new InternalBaseResponse<>(1, "Denied", false));
        assertRatingRejected(new InternalBaseResponse<>(1, "Malformed", null));
        assertRatingRejected(null);
    }

    @Test
    void decisionEligibilityAcceptsCanonicalTrueEnvelope() {
        stub(new InternalBaseResponse<>(1, "Eligible", true));

        decisionClient.requirePendingOrderForRestaurant(10L, 30L);
    }

    @Test
    void decisionEligibilityRejectsFailureFalseNullAndEmptyEnvelopes() {
        assertDecisionRejected(new InternalBaseResponse<>(0, "Denied", true));
        assertDecisionRejected(new InternalBaseResponse<>(1, "Denied", false));
        assertDecisionRejected(new InternalBaseResponse<>(1, "Malformed", null));
        assertDecisionRejected(null);
    }

    private void assertRatingRejected(InternalBaseResponse<Boolean> envelope) {
        stub(envelope);
        assertThatThrownBy(() -> ratingClient.requireDeliveredOrder(10L, 20L, 30L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertDecisionRejected(InternalBaseResponse<Boolean> envelope) {
        stub(envelope);
        assertThatThrownBy(() -> decisionClient.requirePendingOrderForRestaurant(10L, 30L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void stub(InternalBaseResponse<Boolean> envelope) {
        ResponseEntity<InternalBaseResponse<Boolean>> response = envelope == null
                ? ResponseEntity.ok().build()
                : ResponseEntity.ok(envelope);
        doReturn(response).when(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                org.mockito.ArgumentMatchers
                        .<ParameterizedTypeReference<InternalBaseResponse<Boolean>>>any());
    }

    private RestaurantOrderCircuitBreaker circuitBreaker() {
        return new RestaurantOrderCircuitBreaker(new OrderCallResilienceProperties(), new SimpleMeterRegistry());
    }
}
