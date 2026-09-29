package com.delivery.web_bff.auth;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.domain.session.SessionMaterial;
import com.delivery.web_bff.domain.session.SessionFactory;
import com.delivery.web_bff.domain.session.SessionRejectedException;

/** Framework-free login orchestration. HTTP and persistence stay in adapters. */
public final class LoginApplicationService implements UseCases.Login {
    private final Ports.Authentication authentication;
    private final SessionFactory sessions;
    private final Ports.Sessions sessionStore;
    private final Ports.Clock clock;

    public LoginApplicationService(Ports.Authentication authentication, SessionFactory sessions,
            Ports.Sessions sessionStore, Ports.Clock clock) {
        this.authentication = authentication; this.sessions = sessions;
        this.sessionStore = sessionStore; this.clock = clock;
    }

    @Override
    public SessionMaterial execute(Ports.LoginCommand command) {
        if (command == null) throw new SessionRejectedException("Login command is required");
        Ports.AuthenticatedTokens tokens = authentication.login(command);
        if (command.role() != null && !command.role().isBlank()
                && !command.role().equals(tokens.role())) {
            throw new SessionRejectedException("Authenticated role does not match the requested portal");
        }
        SessionMaterial material = sessions.create(tokens.accessToken(), tokens.refreshToken(),
                tokens.principalId(), tokens.email(), tokens.role(), clock.now());
        sessionStore.save(material.session());
        return material;
    }

}
