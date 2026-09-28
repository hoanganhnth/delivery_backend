package com.delivery.web_bff.session;

import com.delivery.web_bff.auth.AuthGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class WebSessionRefreshService {
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private final WebSessionRepository sessions;
    private final TokenVault vault;
    private final AuthGateway auth;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Autowired
    public WebSessionRefreshService(WebSessionRepository sessions, TokenVault vault, AuthGateway auth,
            PlatformTransactionManager transactionManager) {
        this(sessions, vault, auth, Clock.systemUTC(), new TransactionTemplate(transactionManager));
    }

    WebSessionRefreshService(WebSessionRepository sessions, TokenVault vault, AuthGateway auth,
            Clock clock, TransactionTemplate transactions) {
        this.sessions = sessions; this.vault = vault; this.auth = auth; this.clock = clock; this.transactions = transactions;
    }

    public WebSession refresh(String rawSessionId, String csrfToken) {
        Instant now = clock.instant();
        RefreshClaim claim = transactions.execute(status -> claim(rawSessionId, csrfToken, now));
        if (claim == null) throw new RefreshRejectedException("Refresh could not be claimed");

        AuthGateway.AuthTokens tokens;
        try {
            tokens = auth.refresh(claim.refreshToken());
        } catch (RuntimeException unknownOutcome) {
            transactions.executeWithoutResult(status -> revokeClaim(claim, clock.instant()));
            throw new WebSessionService.SessionRejectedException("Refresh outcome is unknown; re-login is required");
        }

        return transactions.execute(status -> complete(claim, tokens, clock.instant()));
    }

    private RefreshClaim claim(String rawSessionId, String csrfToken, Instant now) {
        if (rawSessionId == null || rawSessionId.isBlank() || csrfToken == null || csrfToken.isBlank()) {
            throw new WebSessionService.SessionRejectedException("Web session or CSRF token is missing");
        }
        String hash = SessionFactory.hash(rawSessionId);
        WebSession session = sessions.findActive(hash, now)
                .orElseThrow(() -> new WebSessionService.SessionRejectedException("Web session is not active"));
        if (!constantTimeEquals(session.getCsrfHash(), SessionFactory.hash(csrfToken))) {
            throw new WebSessionService.SessionRejectedException("CSRF token is invalid");
        }
        int claimed = sessions.claimRefresh(hash, session.getGeneration(), now, now.plus(CLAIM_LEASE));
        if (claimed != 1) throw new RefreshRejectedException("Another refresh is already in progress");
        return new RefreshClaim(hash, session.getGeneration(), vault.decrypt(session.getRefreshTokenCipher()),
                session.getPrincipalId());
    }

    private WebSession complete(RefreshClaim claim, AuthGateway.AuthTokens tokens, Instant now) {
        WebSession session = sessions.findById(claim.sessionHash())
                .orElseThrow(() -> new WebSessionService.SessionRejectedException("Web session disappeared"));
        if (session.getGeneration() != claim.generation() || session.getRevokedAt() != null
                || !claim.principalId().equals(tokens.authId())) {
            session.revoke(now);
            sessions.save(session);
            throw new WebSessionService.SessionRejectedException("Session changed while refreshing");
        }
        session.rotate(vault.encrypt(tokens.accessToken()), vault.encrypt(tokens.refreshToken()), vault.version(), now);
        return sessions.save(session);
    }

    private void revokeClaim(RefreshClaim claim, Instant now) {
        sessions.findById(claim.sessionHash()).filter(session -> session.getGeneration() == claim.generation())
                .ifPresent(session -> { session.revoke(now); sessions.save(session); });
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private record RefreshClaim(String sessionHash, long generation, String refreshToken, Long principalId) { }

    public static class RefreshRejectedException extends RuntimeException {
        public RefreshRejectedException(String message) { super(message); }
    }
}
