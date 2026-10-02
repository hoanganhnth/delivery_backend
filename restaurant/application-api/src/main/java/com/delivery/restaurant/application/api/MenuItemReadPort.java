package com.delivery.restaurant.application.api;

import java.util.List;
import java.util.Optional;

public interface MenuItemReadPort {
    List<MenuItemSnapshot> findPublicByRestaurant(Long restaurantId, int limit);

    MenuItemPageSlice pagePublicByRestaurant(Long restaurantId, int page, int size);

    Optional<MenuItemPageSlice> findManaged(MenuItemManagementQuery query);
}
