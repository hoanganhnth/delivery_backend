package com.delivery.web_bff.session;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.domain.session.Security;
import com.delivery.web_bff.domain.session.WebSession;
import com.delivery.web_bff.domain.session.RefreshInProgressException;
import com.delivery.web_bff.domain.session.SessionRejectedException;
import java.time.Duration;

/** Owns the refresh claim lease and generation/principal safety checks. */
public final class RefreshApplicationService implements UseCases.Refresh {
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private final Ports.Sessions sessions; private final Ports.Authentication authentication;
    private final Ports.TokenProtection tokens; private final Ports.Clock clock;

    public RefreshApplicationService(Ports.Sessions sessions, Ports.Authentication authentication,
            Ports.TokenProtection tokens, Ports.Clock clock) {
        this.sessions = sessions; this.authentication = authentication; this.tokens = tokens; this.clock = clock;
    }

    @Override
    public UseCases.SessionView execute(String rawSessionId, String csrfToken) {
        InstantPair time = new InstantPair(clock.now());
        if (rawSessionId == null || rawSessionId.isBlank()) throw rejected("Web session or CSRF token is missing");
        String hash = Security.hash(rawSessionId);
        WebSession session = sessions.active(hash, time.now).orElseThrow(() -> rejected("Web session is not active"));
        if (!session.verifiesCsrf(csrfToken)) throw rejected("CSRF token is invalid");
        long generation = session.generation();
        if (!sessions.claimRefresh(hash, generation, time.now, time.now.plus(CLAIM_LEASE)))
            throw new RefreshInProgressException("Another refresh is already in progress");
        Ports.AuthenticatedTokens replacement;
        try { replacement = authentication.refresh(tokens.reveal(session.refreshTokenCipher())); }
        catch (RuntimeException unknownOutcome) {
            sessions.mutate(hash, current -> {
                if (current.generation() == generation) current.revoke(clock.now());
            });
            throw rejected("Refresh outcome is unknown; re-login is required");
        }
        WebSession current = sessions.mutate(hash, locked -> {
            var completionTime = clock.now();
            if (locked.generation() != generation || !locked.isActive(completionTime)
                    || locked.principalId() != replacement.principalId()) {
                locked.revoke(completionTime);
            } else {
                locked.rotate(tokens.protect(replacement.accessToken()), tokens.protect(replacement.refreshToken()),
                        tokens.keyVersion(), completionTime);
            }
        }).orElseThrow(() -> rejected("Web session disappeared"));
        // Throw after the adapter commits so rejection cannot roll back revocation.
        if (!current.isActive(clock.now())) throw rejected("Session changed while refreshing");
        return new UseCases.SessionView(current.principalId(), current.email(), current.role(), current.generation());
    }

    private SessionRejectedException rejected(String message) { return new SessionRejectedException(message); }
    private record InstantPair(java.time.Instant now) { }
}
