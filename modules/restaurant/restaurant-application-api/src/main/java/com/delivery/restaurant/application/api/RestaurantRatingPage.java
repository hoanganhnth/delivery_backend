package com.delivery.restaurant.application.api;

import java.util.List;

/** Framework-free page result used by rating HTTP adapters. */
public record RestaurantRatingPage(
        List<RestaurantRatingResult> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext) {
}
