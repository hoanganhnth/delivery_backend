package com.delivery.restaurant.application.api;

import java.util.Optional;

public interface MenuItemCreationPort {
    Optional<MenuItemCreateResult> create(
            CreateMenuItemCommand command, MenuItemCreateDecision decision);
}
