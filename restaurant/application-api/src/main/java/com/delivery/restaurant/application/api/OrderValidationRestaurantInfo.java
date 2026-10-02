package com.delivery.restaurant.application.api;

public record OrderValidationRestaurantInfo(Long restaurantId,
            Long creatorId,
            Long ownerPrincipalId,
            String restaurantName,
            String restaurantAddress,
            String restaurantPhone,
            Double latitude,
            Double longitude,
            Integer defaultPrepTimeMinutes,
            Boolean isAvailable,
            Boolean isOpen,
            String operatingHours,
            Boolean serviceabilityEnabled,
            Boolean serviceable,
            Long serviceabilityZoneId,
            Long serviceabilityZoneRevision,
            String serviceabilityReason) { }
