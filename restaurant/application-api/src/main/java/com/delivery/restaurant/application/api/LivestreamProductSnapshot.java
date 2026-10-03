package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.MenuItemStatus;

/** Unfiltered canonical menu facts for the internal Livestream authority. */
public record LivestreamProductSnapshot(Long productId, Long restaurantId, String productName,
        String productImage, String restaurantName, MenuItemStatus status) {}
