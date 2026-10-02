package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.Session;
import com.delivery.auth.domain.policy.CredentialsRejected;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Password login commits device revocation, session and refresh credential together. */
public final class DefaultLoginUseCase implements LoginUseCase {
    private final AuthAccountPort accounts;
    private final PasswordCredentialPort credentials;
    private final SessionPort sessions;
    private final SessionTokenPort tokens;
    private final AuthTransactionPort transactions;
    private final RefreshCredentialPort refreshCredentials;

    public DefaultLoginUseCase(AuthAccountPort accounts, PasswordCredentialPort credentials,
            SessionPort sessions, SessionTokenPort tokens, AuthTransactionPort transactions,
            RefreshCredentialPort refreshCredentials) {
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.sessions = Objects.requireNonNull(sessions);
        this.tokens = Objects.requireNonNull(tokens);
        this.transactions = Objects.requireNonNull(transactions);
        this.refreshCredentials = Objects.requireNonNull(refreshCredentials);
    }

    @Override public AuthenticationResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command");
        return transactions.required(() -> loginInTransaction(command));
    }

    private AuthenticationResult loginInTransaction(LoginCommand command) {
        if (command.email() == null || command.password() == null) {
            throw new CredentialsRejected("Invalid email or password");
        }
        AuthAccount account = accounts.findByEmail(command.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new CredentialsRejected("Invalid email or password"));
        if (!credentials.matches(command.password(), account.passwordHash())) {
            throw new CredentialsRejected("Invalid email or password");
        }
        if (!Boolean.TRUE.equals(account.isActive())) {
            throw new CredentialsRejected("Account is blocked or inactive");
        }
        if (Boolean.TRUE.equals(account.emailVerificationRequired()) && account.emailVerifiedAt() == null) {
            throw new CredentialsRejected("Email verification required");
        }
        if (account.lifecycleStatus() != AuthAccount.LifecycleStatus.ACTIVE) {
            throw new CredentialsRejected("Account onboarding is not complete");
        }
        if (command.deviceId() == null || command.deviceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID must not be empty");
        }
        if (account.userId() == null) {
            throw new CredentialsRejected("Account profile is not provisioned");
        }
        LocalDateTime revokedAt = LocalDateTime.now();
        refreshCredentials.revokeDevice(account.id(), command.deviceId().trim(), revokedAt);
        sessions.deactivateForAccountAndDevice(account.id(), command.deviceId().trim(), revokedAt);
        String access = tokens.issueAccessToken(account);
        String family = UUID.randomUUID().toString();
        String refresh = tokens.issueRefreshToken(account, family);
        LocalDateTime now = LocalDateTime.now();
        Session session = sessions.save(new Session(null, account.id(), command.deviceId(), command.deviceName(),
                command.deviceType(), command.ipAddress(), family, true, now, now.plusDays(7), now));
        refreshCredentials.rememberCurrent(session, refresh, now, session.expiresAt());
        return new AuthenticationResult(access, refresh, account.id(), account.email(), account.role().name());
    }
}
