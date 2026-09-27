package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.time.LocalTime;

/** Trusted actor context and nullable patch values for a Restaurant update. */
public record UpdateRestaurantCommand(
        Long restaurantId,
        Long actorPrincipalId,
        Long legacyUserId,
        RestaurantActorRole actorRole,
        boolean principalOwnershipEnforced,
        String name,
        String address,
        String phone,
        LocalTime openingHour,
        LocalTime closingHour,
        Integer defaultPrepTimeMinutes,
        String image,
        Double latitude,
        Double longitude,
        String description) {}
