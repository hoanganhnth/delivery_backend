package com.delivery.user.application.api;

import java.time.LocalDateTime;

public record UserIdentityProjection(String status, Long version, Boolean blocked, Boolean active,
        LocalDateTime blockedAt, Long blockedBy, String reason) {}
