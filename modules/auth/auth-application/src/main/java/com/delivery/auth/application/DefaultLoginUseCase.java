package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.Session;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Coordinates password verification, session creation, and token issuance. */
public final class DefaultLoginUseCase implements LoginUseCase {
    private final AuthAccountPort accounts;
    private final PasswordCredentialPort credentials;
    private final SessionPort sessions;
    private final TokenPort tokens;

    public DefaultLoginUseCase(AuthAccountPort accounts, PasswordCredentialPort credentials,
            SessionPort sessions, TokenPort tokens) {
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.sessions = Objects.requireNonNull(sessions);
        this.tokens = Objects.requireNonNull(tokens);
    }

    @Override public AuthenticationResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.email() == null || command.password() == null || command.deviceId() == null || command.deviceId().isBlank()) {
            throw new IllegalArgumentException("Email, password, and device ID are required");
        }
        AuthAccount account = accounts.findByEmail(command.email().trim().toLowerCase())
                .filter(a -> credentials.matches(command.password(), a.passwordHash()))
                .filter(AuthAccount::canAuthenticate)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        LocalDateTime now = LocalDateTime.now();
        String family = UUID.randomUUID().toString();
        sessions.deactivateForAccountAndDevice(account.id(), command.deviceId().trim(), now);
        sessions.save(new Session(null, account.id(), command.deviceId().trim(), command.deviceName(), command.deviceType(),
                command.ipAddress(), family, true, now, now.plusDays(7), now));
        return new AuthenticationResult(tokens.issueAccessToken(account), tokens.issueRefreshToken(account, family),
                account.id(), account.email(), account.role().name());
    }
}
