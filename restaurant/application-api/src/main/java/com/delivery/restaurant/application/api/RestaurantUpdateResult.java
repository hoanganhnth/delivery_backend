package com.delivery.restaurant.application.api;

/** Updated projection plus the transitional ownership fallback signal. */
public record RestaurantUpdateResult(
        RestaurantSnapshot snapshot,
        boolean usedLegacyFallback) {}
