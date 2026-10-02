package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.Session.DeviceType;

/** Framework-free social-login input; provider verification is an adapter port. */
public record SocialLoginCommand(
        String provider,
        String providerToken,
        String requestedRole,
        String deviceId,
        String deviceName,
        DeviceType deviceType,
        String ipAddress) {
}
