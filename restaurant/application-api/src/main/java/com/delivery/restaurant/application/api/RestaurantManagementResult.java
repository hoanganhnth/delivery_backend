package com.delivery.restaurant.application.api;

import java.util.List;

/** Management rows plus the legacy-fallback rows observable by host metrics. */
public record RestaurantManagementResult(
        List<RestaurantSnapshot> restaurants,
        int legacyFallbackCount) {}
