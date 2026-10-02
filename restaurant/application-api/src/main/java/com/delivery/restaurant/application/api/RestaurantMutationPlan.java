package com.delivery.restaurant.application.api;

import java.time.LocalTime;

/** Effective values to apply after authorization and validation. */
public record RestaurantMutationPlan(
        Long restaurantId,
        Long ownerPrincipalId,
        String name,
        String address,
        String phone,
        LocalTime openingHour,
        LocalTime closingHour,
        Integer defaultPrepTimeMinutes,
        String image,
        String description,
        Double latitude,
        Double longitude) {}
