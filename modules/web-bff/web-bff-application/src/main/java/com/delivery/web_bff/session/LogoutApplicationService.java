package com.delivery.web_bff.session;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.domain.session.Security;
import com.delivery.web_bff.domain.session.SessionRejectedException;
import com.delivery.web_bff.domain.session.WebSession;

/** Revokes locally before attempting upstream logout; local state is authoritative. */
public final class LogoutApplicationService implements UseCases.Logout {
    private final Ports.Sessions sessions;
    private final Ports.Authentication authentication;
    private final Ports.TokenProtection tokens;
    private final Ports.Clock clock;

    public LogoutApplicationService(Ports.Sessions sessions, Ports.Authentication authentication,
            Ports.TokenProtection tokens, Ports.Clock clock) {
        this.sessions = sessions; this.authentication = authentication;
        this.tokens = tokens; this.clock = clock;
    }

    @Override
    public void execute(String rawSessionId, String csrfToken) {
        WebSession session = require(rawSessionId, csrfToken);
        String refresh = tokens.reveal(session.refreshTokenCipher());
        session.revoke(clock.now());
        sessions.save(session);
        try { authentication.logout(refresh); } catch (RuntimeException ignored) { }
    }

    private WebSession require(String rawSessionId, String csrfToken) {
        if (rawSessionId == null || rawSessionId.isBlank()) throw rejected("Web session is not active");
        WebSession session = sessions.active(Security.hash(rawSessionId), clock.now())
                .orElseThrow(() -> rejected("Web session is not active"));
        if (!session.verifiesCsrf(csrfToken)) throw rejected("CSRF token is invalid");
        return session;
    }

    private SessionRejectedException rejected(String message) { return new SessionRejectedException(message); }
}
