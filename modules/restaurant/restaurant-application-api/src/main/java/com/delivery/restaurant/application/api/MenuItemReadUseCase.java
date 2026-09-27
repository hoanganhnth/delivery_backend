package com.delivery.restaurant.application.api;

import java.util.List;

public interface MenuItemReadUseCase {
    List<MenuItemSnapshot> listPublic(Long restaurantId);

    MenuItemPageSlice pagePublic(Long restaurantId, int page, int size);
}
