package com.delivery.livestream_service.client;

import org.junit.jupiter.api.Test;
import com.delivery.observability.CorrelationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;

class LivestreamProductAuthorityClientTest {
    @Test
    void requestsCanonicalScopeAndAcceptsMatchingMetadata() {
        var builder = RestClient.builder().baseUrl("http://restaurant.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new LivestreamProductAuthorityClient(builder.build(), "test-secret");
        server.expect(requestTo("http://restaurant.test/api/restaurants/internal/42/livestream-products/10"))
            .andExpect(header("Internal-Token", "test-secret"))
            .andExpect(header("X-Correlation-Id", "product-authority"))
            .andRespond(withSuccess("{\"status\":1,\"data\":{\"productId\":10,\"restaurantId\":42,\"productName\":\"Canonical\",\"productImage\":null,\"restaurantName\":\"Kitchen\"}}", MediaType.APPLICATION_JSON));
        try (var ignored = CorrelationContext.with("product-authority")) {
            assertThat(client.requireAvailable(42L, 10L).productName()).isEqualTo("Canonical");
        }
        server.verify();
    }
    @Test
    void failsClosedForForeignMalformedAndMissingProducts() {
        for (String body : new String[] {
            "{\"status\":0,\"data\":null}",
            "{\"status\":4294967297,\"data\":{\"productId\":10,\"restaurantId\":42,\"productName\":\"Canonical\",\"restaurantName\":\"Kitchen\"}}",
            "{\"status\":1,\"data\":{\"productId\":11,\"restaurantId\":42,\"productName\":\"Wrong\"}}",
            "{\"status\":1,\"data\":{\"productId\":10,\"restaurantId\":43,\"productName\":\"Wrong\"}}",
            "{\"status\":1,\"data\":{\"productId\":10.5,\"restaurantId\":42,\"productName\":\"Wrong\"}}"
        }) {
            var builder = RestClient.builder().baseUrl("http://restaurant.test");
            var server = MockRestServiceServer.bindTo(builder).build();
            var client = new LivestreamProductAuthorityClient(builder.build(), "test-secret");
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.requireAvailable(42L, 10L)).isInstanceOf(RuntimeException.class);
            server.verify();
        }
    }
    @Test
    void missingSecretNeverSendsARequest() {
        var builder = RestClient.builder().baseUrl("http://restaurant.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        assertThatThrownBy(() -> new LivestreamProductAuthorityClient(builder.build(), "").requireAvailable(42L, 10L))
            .isInstanceOf(RuntimeException.class);
        server.verify();
    }
}
