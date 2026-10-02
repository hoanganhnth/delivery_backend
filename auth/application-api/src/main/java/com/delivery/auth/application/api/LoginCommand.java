package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.Session.DeviceType;

/** Framework-free password-login input. */
public record LoginCommand(
        String email,
        String password,
        String deviceId,
        String deviceName,
        DeviceType deviceType,
        String ipAddress) {
}
