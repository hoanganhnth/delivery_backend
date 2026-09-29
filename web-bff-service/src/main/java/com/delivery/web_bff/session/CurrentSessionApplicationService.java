package com.delivery.web_bff.session;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.domain.session.Security;
import java.util.Optional;

/** Resolves an opaque browser session without exposing token material. */
public final class CurrentSessionApplicationService implements UseCases.CurrentSession {
    private final Ports.Sessions sessions;
    private final Ports.Clock clock;

    public CurrentSessionApplicationService(Ports.Sessions sessions, Ports.Clock clock) {
        this.sessions = sessions; this.clock = clock;
    }

    @Override
    public Optional<UseCases.SessionView> execute(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) return Optional.empty();
        return sessions.active(Security.hash(rawSessionId), clock.now())
                .map(session -> new UseCases.SessionView(session.principalId(), session.email(), session.role(), session.generation()));
    }
}
