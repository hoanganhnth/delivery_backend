package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.LivestreamProductReadPort;
import com.delivery.restaurant.application.api.LivestreamProductSnapshot;
import com.delivery.restaurant.application.api.LivestreamProductUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import java.util.Objects;
import java.util.Optional;

/** Canonical AVAILABLE and restaurant-scope decisions for Livestream pinning. */
public final class DefaultLivestreamProductUseCase implements LivestreamProductUseCase {
    private final LivestreamProductReadPort products;
    private final RestaurantTransactionPort transactions;

    public DefaultLivestreamProductUseCase(LivestreamProductReadPort products, RestaurantTransactionPort transactions) {
        this.products = Objects.requireNonNull(products);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public Optional<LivestreamProductSnapshot> findAvailable(Long restaurantId, Long productId) {
        if (restaurantId == null || restaurantId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("Invalid product scope");
        }
        return transactions.readOnly(() -> products.findProduct(productId)
                .filter(item -> restaurantId.equals(item.restaurantId()) && item.status() == MenuItemStatus.AVAILABLE));
    }
}
