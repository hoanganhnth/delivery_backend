package com.delivery.user.application.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Framework-free profile projection returned by profile use cases. */
public record UserProfileResult(
        Long id,
        Long authId,
        Long principalId,
        String identityStatus,
        Long identityStatusVersion,
        String email,
        String role,
        String fullName,
        String phone,
        LocalDate dob,
        String avatarUrl,
        String address,
        Boolean isActive,
        Boolean isBlocked,
        LocalDateTime blockedAt,
        Long blockedBy,
        String blockReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
