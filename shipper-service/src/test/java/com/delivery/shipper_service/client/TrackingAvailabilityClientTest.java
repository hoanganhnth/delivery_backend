package com.delivery.shipper_service.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrackingAvailabilityClientTest {

    @Mock
    private RestTemplate restTemplate;

    private TrackingAvailabilityClient client;

    @BeforeEach
    void setUp() {
        client = new TrackingAvailabilityClient(restTemplate, "http://tracking-service:8093", "shared-secret");
    }

    @Test
    void sendsCredentialProtectedInternalOfflineCommand() {
        stub(ResponseEntity.ok(new TrackingAvailabilityResponse<Void>(1, null, "Shipper marked offline")));

        client.markOffline(42L);

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<?>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(url.capture(), eq(HttpMethod.POST), request.capture(),
                org.mockito.ArgumentMatchers
                        .<ParameterizedTypeReference<TrackingAvailabilityResponse<Void>>>any());
        assertThat(url.getValue()).isEqualTo("http://tracking-service:8093/api/tracking/internal/shippers/42/offline");
        assertThat(request.getValue().getHeaders().getFirst("Internal-Token")).isEqualTo("shared-secret");
    }

    @Test
    void rejectsInvalidOrFailedTrackingResponses() {
        stub(ResponseEntity.ok(new TrackingAvailabilityResponse<Void>(0, null, "Forbidden")));

        assertThatThrownBy(() -> client.markOffline(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tracking rejected");
    }

    @Test
    void failsClosedWhenInternalCredentialIsMissing() {
        TrackingAvailabilityClient withoutSecret =
                new TrackingAvailabilityClient(restTemplate, "http://tracking-service:8093", " ");

        assertThatThrownBy(() -> withoutSecret.markOffline(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_SECRET");
    }

    private void stub(ResponseEntity<TrackingAvailabilityResponse<Void>> response) {
        doReturn(response).when(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                org.mockito.ArgumentMatchers
                        .<ParameterizedTypeReference<TrackingAvailabilityResponse<Void>>>any());
    }
}
