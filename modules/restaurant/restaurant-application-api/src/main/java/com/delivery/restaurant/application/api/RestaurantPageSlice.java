package com.delivery.restaurant.application.api;

import java.util.List;

/** Exact page metadata without exposing Spring Data types. */
public record RestaurantPageSlice(
        List<RestaurantSnapshot> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext) {}
