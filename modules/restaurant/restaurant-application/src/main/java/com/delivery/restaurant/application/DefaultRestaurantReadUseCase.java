package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.RestaurantPageSlice;
import com.delivery.restaurant.application.api.RestaurantReadPort;
import com.delivery.restaurant.application.api.RestaurantReadUseCase;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Routes public/history reads to the public projection port. */
public final class DefaultRestaurantReadUseCase implements RestaurantReadUseCase {

    private static final int PUBLIC_LIMIT = 100;
    private final RestaurantReadPort readPort;

    public DefaultRestaurantReadUseCase(RestaurantReadPort readPort) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
    }

    @Override
    public Optional<RestaurantSnapshot> findById(Long id) {
        return readPort.findById(Objects.requireNonNull(id, "id"));
    }

    @Override
    public List<RestaurantSnapshot> listPublic() {
        return readPort.findPublic(PUBLIC_LIMIT);
    }

    @Override
    public List<RestaurantSnapshot> searchPublic(String keyword) {
        return readPort.searchPublic(keyword, PUBLIC_LIMIT);
    }

    @Override
    public RestaurantPageSlice pagePublic(int page, int size, String keyword) {
        String normalizedKeyword = keyword == null || keyword.isBlank()
                ? keyword : keyword.trim();
        return readPort.pagePublic(page, size, normalizedKeyword);
    }
}
