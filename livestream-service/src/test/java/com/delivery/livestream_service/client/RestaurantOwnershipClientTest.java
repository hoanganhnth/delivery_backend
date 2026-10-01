package com.delivery.livestream_service.client;

import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.observability.CorrelationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RestaurantOwnershipClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://restaurant.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestaurantOwnershipClient client = new RestaurantOwnershipClient(builder.build(), "test-secret");

    @Test
    void acceptsOnlyCanonicalOwnershipAndPropagatesCorrelation() {
        server.expect(requestTo("http://restaurant.test/api/restaurants/internal/42/owners/7?legacyOwnerId=9"))
                .andExpect(header("Internal-Token", "test-secret"))
                .andExpect(header("X-Correlation-Id", "ownership-check"))
                .andRespond(withSuccess("{\"status\":1,\"data\":true}", MediaType.APPLICATION_JSON));
        try (var ignored = CorrelationContext.with("ownership-check")) {
            client.requireOwnedBy(42L, 7L, 9L);
        }
        server.verify();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidIdentityFailsBeforeAnyRequest(Long id) {
        assertThatThrownBy(() -> client.requireOwnedBy(id, 7L, 9L)).isInstanceOf(UnauthorizedLivestreamAccessException.class);
        assertThatThrownBy(() -> client.requireOwnedBy(42L, id, 9L)).isInstanceOf(UnauthorizedLivestreamAccessException.class);
        assertThatThrownBy(() -> client.requireOwnedBy(42L, 7L, id)).isInstanceOf(UnauthorizedLivestreamAccessException.class);
        server.verify();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void absentSecretFailsBeforeAnyRequest(String secret) {
        assertThatThrownBy(() -> new RestaurantOwnershipClient(builder.build(), secret).requireOwnedBy(42L, 7L, 9L))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"status\":0,\"data\":true}",
            "{\"status\":\"1\",\"data\":true}", "{\"status\":1,\"data\":false}",
            "{\"status\":1,\"data\":\"true\"}", "{\"status\":1,\"data\":null}"})
    void malformedOrDeniedEnvelopeFailsClosed(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.requireOwnedBy(42L, 7L, 9L))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        server.verify();
    }

    @Test
    void downstreamFailureDoesNotLeakResponseOrCredentials() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .body("private-host test-secret").contentType(MediaType.TEXT_PLAIN));
        assertThatThrownBy(() -> client.requireOwnedBy(42L, 7L, 9L))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class)
                .hasMessage("Restaurant ownership cannot be verified");
        server.verify();
    }
}
