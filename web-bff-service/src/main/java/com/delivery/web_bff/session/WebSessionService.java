package com.delivery.web_bff.session;

import com.delivery.web_bff.auth.AuthGateway;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class WebSessionService {
    private final WebSessionRepository sessions;
    private final SessionFactory factory;
    private final TokenVault vault;
    private final AuthGateway auth;
    private final Clock clock;

    @Autowired
    public WebSessionService(WebSessionRepository sessions, SessionFactory factory, TokenVault vault, AuthGateway auth) {
        this(sessions, factory, vault, auth, Clock.systemUTC());
    }

    WebSessionService(WebSessionRepository sessions, SessionFactory factory, TokenVault vault,
            AuthGateway auth, Clock clock) {
        this.sessions = sessions; this.factory = factory; this.vault = vault; this.auth = auth; this.clock = clock;
    }

    @Transactional
    public SessionMaterial login(String email, String password, String role, String deviceName) {
        AuthGateway.AuthTokens tokens = auth.login(new AuthGateway.LoginCommand(
                email, password, role, "web-bff-" + UUID.randomUUID(), deviceName));
        if (role != null && !role.isBlank() && !role.equals(tokens.role())) {
            throw new SessionRejectedException("Authenticated role does not match the requested portal");
        }
        SessionMaterial material = factory.create(tokens.accessToken(), tokens.refreshToken(), tokens.authId(),
                tokens.email(), tokens.role(), clock.instant());
        sessions.save(material.session());
        return material;
    }

    @Transactional(readOnly = true)
    public Optional<WebSession> findActive(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) return Optional.empty();
        return sessions.findActive(SessionFactory.hash(rawSessionId), clock.instant());
    }

    @Transactional(readOnly = true)
    public String accessToken(String rawSessionId, String csrfToken, boolean mutation) {
        WebSession session = mutation
                ? requireActive(rawSessionId, csrfToken)
                : findActive(rawSessionId).orElseThrow(
                        () -> new SessionRejectedException("Web session is not active"));
        return vault.decrypt(session.getAccessTokenCipher());
    }

    @Transactional
    public void logout(String rawSessionId, String csrfToken) {
        WebSession session = requireActive(rawSessionId, csrfToken);
        String refreshToken = vault.decrypt(session.getRefreshTokenCipher());
        session.revoke(clock.instant());
        sessions.save(session);
        try {
            auth.logout(refreshToken);
        } catch (RuntimeException ignored) {
            // Local revocation is authoritative and this session can never use
            // the token again. The upstream token may remain live until its own
            // expiry; monitor logout failures and never re-enable this session.
        }
    }

    @Transactional(readOnly = true)
    public WebSession requireActive(String rawSessionId, String csrfToken) {
        WebSession session = findActive(rawSessionId)
                .orElseThrow(() -> new SessionRejectedException("Web session is not active"));
        if (csrfToken == null || !constantTimeEquals(session.getCsrfHash(), SessionFactory.hash(csrfToken))) {
            throw new SessionRejectedException("CSRF token is invalid");
        }
        return session;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public static class SessionRejectedException extends RuntimeException {
        public SessionRejectedException(String message) { super(message); }
    }
}
