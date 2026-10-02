package com.delivery.user.application.api;

import java.time.LocalDate;

/** Nullable profile patch; Auth-owned identity fields are intentionally absent. */
public record UpdateProfileCommand(
        Long userId,
        String fullName,
        String phone,
        LocalDate dob,
        String avatarUrl,
        String address) {
}
