package com.delivery.user.application.api;

import java.time.LocalDate;

/** Trusted profile-creation input; identity values originate from Auth. */
public record CreateUserCommand(
        Long authId,
        Long principalId,
        String email,
        String role,
        String fullName,
        String phone,
        LocalDate dob,
        String avatarUrl,
        String address) {
}
