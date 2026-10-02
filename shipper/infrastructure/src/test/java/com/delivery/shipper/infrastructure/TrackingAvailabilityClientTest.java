package com.delivery.shipper.infrastructure;

import com.delivery.shipper.infrastructure.client.TrackingAvailabilityClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingAvailabilityClientTest {
    @Test void rejectsMissingInternalSecretBeforeMakingHttpCall() {
        TrackingAvailabilityClient client = new TrackingAvailabilityClient(new RestTemplate(), "http://tracking", " ");
        assertThatThrownBy(() -> client.markOffline(7L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_SECRET");
    }

    @Test void failsClosedWhenTrackingReturnsNonSuccessEnvelope() {
        RestTemplate rest = new RestTemplate() {
            @Override public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> request,
                    ParameterizedTypeReference<T> type, Object... variables) {
                return ResponseEntity.ok(null);
            }
        };
        TrackingAvailabilityClient client = new TrackingAvailabilityClient(rest, "http://tracking", "secret");
        assertThatThrownBy(() -> client.markOffline(7L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rejected");
    }
}
