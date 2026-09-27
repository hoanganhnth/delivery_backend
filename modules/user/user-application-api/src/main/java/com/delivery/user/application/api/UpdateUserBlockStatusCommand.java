package com.delivery.user.application.api;

/** Trusted command for projecting Auth-owned account block status to User. */
public record UpdateUserBlockStatusCommand(
        Long userId,
        Long adminId,
        Boolean blocked,
        String reason) {
}
