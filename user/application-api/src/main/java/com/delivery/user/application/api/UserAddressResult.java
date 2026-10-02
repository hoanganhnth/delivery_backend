package com.delivery.user.application.api;

import java.time.LocalDateTime;

/** Framework-free delivery-address projection returned by the application. */
public record UserAddressResult(
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
