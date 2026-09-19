package com.delivery.web_bff.auth;

public interface AuthGateway {
    AuthTokens login(LoginCommand command);
    AuthTokens refresh(String refreshToken);
    void logout(String refreshToken);

    record LoginCommand(String email, String password, String role, String deviceId, String deviceName) { }
    record AuthTokens(String accessToken, String refreshToken, Long authId, String email, String role) { }

    final class AuthenticationRejectedException extends RuntimeException {
        public AuthenticationRejectedException() {
            super("Invalid email or password");
        }
    }
}
