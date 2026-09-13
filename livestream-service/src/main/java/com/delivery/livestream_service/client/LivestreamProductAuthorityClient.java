package com.delivery.livestream_service.client;

import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LivestreamProductAuthorityClient {
    private final RestClient client;
    private final String secret;
    public record Product(long productId, long restaurantId, String productName,
                          String productImage, String restaurantName) {}

    @Autowired
    public LivestreamProductAuthorityClient(
            @Value("${restaurant.service.url:http://restaurant-service:8083}") String url,
            @Value("${app.internal.secret:}") String secret) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.secret = secret;
    }

    LivestreamProductAuthorityClient(RestClient client, String secret) {
        this.client = client;
        this.secret = secret;
    }

    public Product requireAvailable(Long restaurantId, Long productId) {
        if (restaurantId == null || restaurantId <= 0 || productId == null || productId <= 0 ||
                secret == null || secret.isBlank()) throw denied();
        try {
            JsonNode envelope = client.get()
                    .uri("/api/restaurants/internal/{restaurantId}/livestream-products/{productId}", restaurantId, productId)
                    .header("Internal-Token", secret).retrieve().body(JsonNode.class);
            if (envelope == null || !envelope.path("status").isIntegralNumber() ||
                    envelope.path("status").asInt() != 1) throw denied();
            JsonNode data = envelope.path("data");
            if (!matchesId(data.path("productId"), productId) ||
                    !matchesId(data.path("restaurantId"), restaurantId) ||
                    !data.path("productName").isTextual() || data.path("productName").asText().isBlank() ||
                    !data.path("restaurantName").isTextual() || data.path("restaurantName").asText().isBlank()) throw denied();
            JsonNode image = data.path("productImage");
            if (!image.isMissingNode() && !image.isNull() && !image.isTextual()) throw denied();
            return new Product(productId, restaurantId, data.path("productName").asText(),
                    image.isTextual() ? image.asText() : null, data.path("restaurantName").asText());
        } catch (RuntimeException error) {
            // Do not leak downstream response bodies, addresses, or credentials.
            throw denied();
        }
    }

    private static boolean matchesId(JsonNode value, long expected) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() == expected;
    }
    private static UnauthorizedLivestreamAccessException denied() {
        return new UnauthorizedLivestreamAccessException("Không thể xác minh món đang bán của nhà hàng");
    }
}
