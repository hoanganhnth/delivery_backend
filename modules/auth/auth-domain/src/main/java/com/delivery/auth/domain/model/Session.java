package com.delivery.auth.domain.model;

import java.time.LocalDateTime;

/** Framework-independent device session and refresh-token family boundary. */
public record Session(
        Long id,
        Long accountId,
        String deviceId,
        String deviceName,
        DeviceType deviceType,
        String ipAddress,
        String tokenFamilyId,
        Boolean isActive,
        LocalDateTime lastLoginAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {

    public enum DeviceType {
        MOBILE,
        WEB,
        TABLET;

        public static DeviceType fromString(String value) {
            if (value == null) {
                return null;
            }
            return DeviceType.valueOf(value.trim().toUpperCase());
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** A session is usable only while active and strictly before expiry. */
    public boolean isUsableAt(LocalDateTime now) {
        return now != null
                && Boolean.TRUE.equals(isActive)
                && expiresAt != null
                && expiresAt.isAfter(now);
    }

    public boolean belongsToAccount(Long expectedAccountId) {
        return expectedAccountId != null && expectedAccountId.equals(accountId);
    }

    public boolean belongsToTokenFamily(String expectedTokenFamilyId) {
        return expectedTokenFamilyId != null
                && expectedTokenFamilyId.equals(tokenFamilyId);
    }
}
