package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import java.time.LocalTime;

/** Persisted creation snapshot, including defaults, without a second database read. */
public record CreateRestaurantResult(
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
        String timeZone) {}
