package com.delivery.flashsale_service.client;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class RestaurantOwnershipClientTest {

    @Mock
    private RestTemplate restTemplate;

    private RestaurantOwnershipClient client;

    @BeforeEach
    void setUp() {
        client = new RestaurantOwnershipClient(
                restTemplate,
                "http://restaurant-service",
                "test-secret");
    }

    @Test
    void acceptsCanonicalTrueEnvelope() {
        stub(new InternalBaseResponse<>(1, "Owned", true));

        client.requireOwnedBy(10L, 20L);
    }

    @Test
    void rejectsFailureFalseNullAndEmptyEnvelopes() {
        assertRejected(new InternalBaseResponse<>(0, "Denied", true));
        assertRejected(new InternalBaseResponse<>(1, "Denied", false));
        assertRejected(new InternalBaseResponse<>(1, "Malformed", null));
        assertRejected(null);
    }

    private void assertRejected(InternalBaseResponse<Boolean> envelope) {
        stub(envelope);
        assertThatThrownBy(() -> client.requireOwnedBy(10L, 20L))
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
}
