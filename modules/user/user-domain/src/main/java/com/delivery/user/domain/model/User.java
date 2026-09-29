package com.delivery.user.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Framework-independent user profile and identity projection.
 *
 * <p>Persistence adapters own the mapping of this model to the existing
 * {@code users} table. The model deliberately carries both the canonical
 * principal identity and the legacy auth column while the migration is in
 * progress.
 */
public record User(
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
