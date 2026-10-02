package com.delivery.web_bff.application.api;

import com.delivery.web_bff.domain.session.WebSession;
import java.time.Instant;
import java.util.Optional;

/** Dependency inversion boundary; adapters own HTTP, ORM and framework details. */
public interface Ports {
    public interface Authentication {
        AuthenticatedTokens login(LoginCommand command);
        AuthenticatedTokens refresh(String refreshToken);
        void logout(String refreshToken);
    }
    public interface Sessions {
        void save(WebSession session);
        Optional<WebSession> active(String sessionHash, Instant now);
        Optional<WebSession> byHash(String sessionHash);
        /** Runs the state transition under an exclusive lock and commits before returning.
         * A callback exception rolls back the transition. Network calls belong outside it. */
        Optional<WebSession> mutate(String sessionHash, java.util.function.Consumer<WebSession> transition);
        boolean claimRefresh(String sessionHash, long generation, Instant now, Instant leaseUntil);
    }
    public interface Clock { Instant now(); }
    public interface TokenProtection extends com.delivery.web_bff.domain.session.TokenProtection { }
    public interface Randomness { byte[] bytes(int size); }
    public interface ApiPolicy { boolean allows(HttpVerb method, String path); }
    public interface ProxyForwarding {
        ForwardedResponse forward(HttpVerb method, String path, String query, java.util.Map<String, java.util.List<String>> headers,
                byte[] body, String bearerToken);
    }
    public record AuthenticatedTokens(String accessToken, String refreshToken, long principalId, String email, String role) { }
    public record LoginCommand(String email, String password, String role, String deviceName, String deviceId) { }
    public enum HttpVerb { GET, HEAD, OPTIONS, POST, PUT, PATCH, DELETE }
    public record ForwardedResponse(int status, java.util.Map<String, java.util.List<String>> headers, byte[] body) { }
}
