package com.delivery.user.domain.model;

import java.time.LocalDateTime;

/** Framework-independent delivery address belonging to a user profile. */
public record UserAddress(
        Long id,
        Long userId,
        String label,
        String recipientName,
        String phoneNumber,
        String addressLine,
        String ward,
        String district,
        String city,
        String postalCode,
        Double latitude,
        Double longitude,
        Boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
