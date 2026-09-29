package com.delivery.web_bff.auth;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public class HttpAuthGateway implements AuthGateway {
    private final RestClient client;

    public HttpAuthGateway(RestClient authRestClient) { this.client = authRestClient; }

    @Override
    public AuthTokens login(LoginCommand command) {
        try {
            AuthEnvelope response = client.post().uri("/api/auth/login")
                    .body(Map.of(
                            "email", command.email(),
                            "password", command.password(),
                            "role", command.role(),
                            "deviceId", command.deviceId(),
                            "deviceName", command.deviceName(),
                            "deviceType", "WEB"))
                    .retrieve().body(AuthEnvelope.class);
            return tokens(response, "login");
        } catch (HttpClientErrorException.Unauthorized ignored) {
            throw new AuthenticationRejectedException();
        }
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        AuthEnvelope response = client.post().uri("/api/auth/refresh-token")
                .body(Map.of("refreshToken", refreshToken)).retrieve().body(AuthEnvelope.class);
        return tokens(response, "refresh");
    }

    @Override
    public void logout(String refreshToken) {
        client.post().uri("/api/auth/logout").body(Map.of("refreshToken", refreshToken)).retrieve().toBodilessEntity();
    }

    private AuthTokens tokens(AuthEnvelope response, String operation) {
        if (response == null || response.status() != 1 || response.data() == null) {
            throw new IllegalStateException("Auth " + operation + " returned an invalid response");
        }
        AuthData data = response.data();
        if (blank(data.accessToken()) || blank(data.refreshToken()) || data.authId() == null
                || blank(data.email()) || blank(data.role())) {
            throw new IllegalStateException("Auth " + operation + " omitted required session data");
        }
        return new AuthTokens(data.accessToken(), data.refreshToken(), data.authId(), data.email(), data.role());
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private record AuthEnvelope(int status, String message, AuthData data) { }
    private record AuthData(String accessToken, String refreshToken, Long authId, String email, String role) { }
}
