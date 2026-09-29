package com.delivery.auth.application.api;

/** Token pair and compatibility identity fields returned by Auth login flows. */
public record AuthenticationResult(
        String accessToken,
        String refreshToken,
        Long authId,
        String email,
        String role) {
}
