package com.delivery.user.application.api;

import java.time.LocalDateTime;

/** Framework-free block-status projection returned after a status mutation. */
public record UserBlockStatusResult(
        Long userId,
        Boolean blocked,
        Boolean active,
        LocalDateTime blockedAt,
        Long blockedBy,
        String reason) {
}
