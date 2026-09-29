package com.delivery.user.application.api;

/** Opaque Auth handoff and profile fields for customer registration. */
public record RegisterUserCommand(
        String provisioningToken,
        String fullName,
        String phone,
        java.time.LocalDate dob,
        String avatarUrl,
        String address) {
}
