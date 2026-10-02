package com.delivery.restaurant.application.api;

import java.util.Optional;

public interface MenuItemManagementReadUseCase {
    Optional<MenuItemPageSlice> read(MenuItemManagementQuery query);
}
