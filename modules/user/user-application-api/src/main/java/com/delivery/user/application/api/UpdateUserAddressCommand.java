package com.delivery.user.application.api;

/** Framework-free input for updating a delivery address. */
public record UpdateUserAddressCommand(
        Long id,
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
        Boolean isDefault) {
}
