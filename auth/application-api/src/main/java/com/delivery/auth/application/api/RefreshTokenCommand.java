package com.delivery.auth.application.api;

/** Framework-free refresh-token rotation input. */
public record RefreshTokenCommand(String refreshToken) {
}
