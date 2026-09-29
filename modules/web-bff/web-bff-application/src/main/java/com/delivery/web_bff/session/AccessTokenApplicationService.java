package com.delivery.web_bff.session;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.domain.session.Security;
import com.delivery.web_bff.domain.session.SessionRejectedException;
import com.delivery.web_bff.domain.session.WebSession;

/** Resolves the server-held bearer token and applies CSRF to mutations. */
public final class AccessTokenApplicationService implements UseCases.AccessTokenResolution {
    private final Ports.Sessions sessions;
    private final Ports.TokenProtection tokens;
    private final Ports.Clock clock;

    public AccessTokenApplicationService(Ports.Sessions sessions, Ports.TokenProtection tokens, Ports.Clock clock) {
        this.sessions = sessions; this.tokens = tokens; this.clock = clock;
    }

    @Override
    public String execute(String rawSessionId, String csrfToken, boolean mutation) {
        if (rawSessionId == null || rawSessionId.isBlank()) throw rejected("Web session is not active");
        WebSession session = sessions.active(Security.hash(rawSessionId), clock.now())
                .orElseThrow(() -> rejected("Web session is not active"));
        if (mutation && !session.verifiesCsrf(csrfToken)) throw rejected("CSRF token is invalid");
        return tokens.reveal(session.accessTokenCipher());
    }

    private SessionRejectedException rejected(String message) { return new SessionRejectedException(message); }
}
