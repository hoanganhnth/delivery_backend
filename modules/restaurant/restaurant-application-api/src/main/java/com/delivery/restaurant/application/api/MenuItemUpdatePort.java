package com.delivery.restaurant.application.api;

import java.util.Optional;

public interface MenuItemUpdatePort {
    Optional<MenuItemUpdateResult> update(
            UpdateMenuItemCommand command, MenuItemUpdateDecision decision);
}
