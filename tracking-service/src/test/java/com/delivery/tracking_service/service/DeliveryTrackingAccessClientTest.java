package com.delivery.tracking_service.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class DeliveryTrackingAccessClientTest {

    @Mock
    private RestTemplate restTemplate;

    private DeliveryTrackingAccessClient client;

    @BeforeEach
    void setUp() {
        client = new DeliveryTrackingAccessClient(restTemplate, "http://delivery-service", "test-secret");
    }

    @Test
    void returnsCanonicalBooleanData() {
        stub(new InternalBaseResponse<>(1, "Allowed", true));
        assertThat(client.canTrack(10L, 20L, "USER", null)).isTrue();

        stub(new InternalBaseResponse<>(1, "Denied", false));
        assertThat(client.canTrack(10L, 20L, "USER", null)).isFalse();
    }

    @Test
    void rejectsFailureNullDataAndEmptyEnvelopes() {
        assertRejected(new InternalBaseResponse<>(0, "Failure", true));
        assertRejected(new InternalBaseResponse<>(1, "Malformed", null));
        assertRejected(null);
    }

    private void assertRejected(InternalBaseResponse<Boolean> envelope) {
        stub(envelope);
        assertThatThrownBy(() -> client.canTrack(10L, 20L, "USER", null))
                .isInstanceOf(IllegalStateException.class);
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
