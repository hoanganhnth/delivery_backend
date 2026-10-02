package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemReadPort;
import com.delivery.restaurant.application.api.MenuItemReadUseCase;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import java.util.List;
import java.util.Objects;

/** Routes public Menu reads to the port-owned AVAILABLE/non-archived projection. */
public final class DefaultMenuItemReadUseCase implements MenuItemReadUseCase {
    private static final int PUBLIC_LIMIT = 100;

    private final MenuItemReadPort readPort;

    public DefaultMenuItemReadUseCase(MenuItemReadPort readPort) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
    }

    @Override
    public List<MenuItemSnapshot> listPublic(Long restaurantId) {
        return readPort.findPublicByRestaurant(
                Objects.requireNonNull(restaurantId, "restaurantId"), PUBLIC_LIMIT);
    }

    @Override
    public MenuItemPageSlice pagePublic(Long restaurantId, int page, int size) {
        if (page < 0 || size < 1 || size > PUBLIC_LIMIT) {
            throw new IllegalArgumentException("Invalid page or size");
        }
        return readPort.pagePublicByRestaurant(
                Objects.requireNonNull(restaurantId, "restaurantId"), page, size);
    }
}
