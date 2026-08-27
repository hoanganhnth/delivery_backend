package com.delivery.livestream_service.client;

import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Server-only bridge to the canonical restaurant ownership boundary. */
@Component
public class RestaurantOwnershipClient {
    private final RestClient client;
    private final String internalSecret;

    public RestaurantOwnershipClient(
            @Value("${restaurant.service.url:http://restaurant-service:8083}") String restaurantUrl,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.client = RestClient.builder().baseUrl(restaurantUrl).build();
        this.internalSecret = internalSecret;
    }

    public void requireOwnedBy(Long restaurantId, Long principalId, Long legacyUserId) {
        if (restaurantId == null || principalId == null || legacyUserId == null
                || internalSecret == null || internalSecret.isBlank()) {
            throw new UnauthorizedLivestreamAccessException("Restaurant ownership cannot be verified");
        }
        try {
            Map<String, Object> envelope = client.get()
                    .uri("/api/restaurants/internal/{restaurantId}/owners/{principalId}?legacyOwnerId={legacyUserId}",
                            restaurantId, principalId, legacyUserId)
                    .header("Internal-Token", internalSecret)
                    .retrieve()
                    .body(Map.class);
            if (envelope == null || !Integer.valueOf(1).equals(envelope.get("status"))
                    || !Boolean.TRUE.equals(envelope.get("data"))) {
                throw new UnauthorizedLivestreamAccessException("You do not own this restaurant");
            }
        } catch (UnauthorizedLivestreamAccessException denied) {
            throw denied;
        } catch (RuntimeException unavailable) {
            throw new UnauthorizedLivestreamAccessException("Restaurant ownership cannot be verified");
        }
    }
}
