package com.delivery.promotion_service.service;

import com.delivery.observability.CorrelationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    void canonicalOwnerRequestCarriesStableAndLegacyIdentitiesAndCorrelation() {
        server.expect(requestTo("http://restaurant.test/api/restaurants/internal/42/owners/7?legacyOwnerId=9"))
                .andExpect(header("Internal-Token", "test-secret"))
                .andExpect(header("X-Correlation-Id", "promotion-owner"))
                .andRespond(withSuccess("{\"status\":1,\"data\":true}", MediaType.APPLICATION_JSON));
        try (var ignored = CorrelationContext.with("promotion-owner")) {
            assertThat(client.isOwnedBy(42L, 7L, 9L)).isTrue();
        }
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4294967297", "1.5", "9223372036854775809"})
    void numericStatusCannotBeTruncatedToSuccess(String status) {
        server.expect(anything()).andRespond(withSuccess("{\"status\":" + status + ",\"data\":true}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.isOwnedBy(42L, 7L, 9L)).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void negativeOwnershipResponseDoesNotGrantAccess() {
        server.expect(anything()).andRespond(withSuccess("{\"status\":1,\"data\":false}", MediaType.APPLICATION_JSON));
        assertThat(client.isOwnedBy(42L, 7L, 9L)).isFalse();
        server.verify();
    }
}
