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
        if (rawSessionId == null || rawSessionId.isBlank()) throw rejected("Web session is not active");
        String[] refresh = new String[1];
        sessions.mutate(Security.hash(rawSessionId), session -> {
            if (!session.isActive(clock.now())) throw rejected("Web session is not active");
            if (!session.verifiesCsrf(csrfToken)) throw rejected("CSRF token is invalid");
            refresh[0] = tokens.reveal(session.refreshTokenCipher());
            session.revoke(clock.now());
        }).orElseThrow(() -> rejected("Web session is not active"));
        try { authentication.logout(refresh[0]); } catch (RuntimeException ignored) { }
    }

    private SessionRejectedException rejected(String message) { return new SessionRejectedException(message); }
}
