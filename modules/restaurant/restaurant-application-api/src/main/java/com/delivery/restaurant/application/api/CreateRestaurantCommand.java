package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.time.LocalTime;

/** Creation input; actor identity is supplied by the trusted host, not the request body. */
public record CreateRestaurantCommand(
        Long actorPrincipalId,
        Long creatorId,
        RestaurantActorRole actorRole,
        Long requestedOwnerPrincipalId,
        String name,
        String address,
        String phone,
        LocalTime openingHour,
        LocalTime closingHour,
        Integer defaultPrepTimeMinutes,
        String image,
        Double addressLat,
        Double addressLng,
        String description) {}
