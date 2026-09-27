package com.delivery.restaurant.application.api;

import java.util.Optional;

public interface CreateMenuItemUseCase {
    Optional<MenuItemCreateResult> create(CreateMenuItemCommand command);
}
