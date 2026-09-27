package com.delivery.restaurant.application.api;

import java.util.Optional;

public interface UpdateMenuItemUseCase {
    Optional<MenuItemUpdateResult> update(UpdateMenuItemCommand command);
}
