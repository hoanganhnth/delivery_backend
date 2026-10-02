package com.delivery.restaurant.application.api;

import java.time.LocalTime;

/** Immutable database facts supplied to the framework-free update decision. */
public record RestaurantStoredFacts(
        Long restaurantId,
        Long ownerPrincipalId,
        Long creatorId,
        String name,
        String address,
        String phone,
        LocalTime openingHour,
        LocalTime closingHour,
        Integer defaultPrepTimeMinutes,
        String image,
        String description,
        Double latitude,
        Double longitude,
        String timeZone) {}
