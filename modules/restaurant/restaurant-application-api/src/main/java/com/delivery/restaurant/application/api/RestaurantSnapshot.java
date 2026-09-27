package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import java.time.LocalTime;

/** Framework-free Restaurant projection used by the host response adapter. */
public record RestaurantSnapshot(
        Long id,
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
        Double rating,
        Integer ratingCount,
        RestaurantStatus lifecycleStatus,
        Long version,
        String timeZone,
        Long ownerPrincipalId,
        Long creatorId) {}
