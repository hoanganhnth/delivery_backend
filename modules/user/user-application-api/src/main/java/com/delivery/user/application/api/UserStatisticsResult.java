package com.delivery.user.application.api;

/** Framework-free administrative user-count projection. */
public record UserStatisticsResult(
        Long totalUsers,
        Long userCount,
        Long adminCount,
        Long shipperCount,
        Long shopOwnerCount,
        Long activeUsers,
        Long blockedUsers) {
}
