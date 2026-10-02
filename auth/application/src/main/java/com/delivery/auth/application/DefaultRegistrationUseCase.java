package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import java.util.Objects;
import java.util.Locale;
import com.delivery.auth.domain.policy.RegistrationIdentityConflict;
import com.delivery.auth.domain.policy.CredentialsRejected;

/** Coordinates public registration while keeping policy and persistence behind ports. */
public final class DefaultRegistrationUseCase implements RegistrationUseCase {
    private final AuthAccountPort accounts;
    private final PasswordCredentialPort credentials;
    private final ProvisioningTokenPort tokens;
    private final RegistrationHandlePort handles;

    public DefaultRegistrationUseCase(AuthAccountPort accounts, PasswordCredentialPort credentials, ProvisioningTokenPort tokens, RegistrationHandlePort handles) {
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.tokens = Objects.requireNonNull(tokens);
        this.handles = Objects.requireNonNull(handles);
    }

    @Override public RegistrationResult register(RegisterCommand command) {
        Objects.requireNonNull(command, "command");
        AuthAccount.Role role = PublicRegistrationRole.parse(command.role());
        if (command.email() == null || command.email().isBlank() || command.password() == null || command.password().isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        AuthAccount saved = accounts.findByEmail(email)
                .map(existing -> resume(existing, command, role))
                .orElseGet(() -> {
                    AuthAccount candidate = new AuthAccount(null, null, AuthAccount.LifecycleStatus.PENDING_PROFILE, 0L,
                            email, credentials.hash(command.password()), role, true, true, null, false,
                            0L, null, null, 0, null, false, null, null, 0L, null, null, null);
                    return accounts.createOrResume(candidate, winner -> resume(winner, command, role));
                });
        String provisioningToken = tokens.issueProvisioningToken(saved);
        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusMinutes(15);
        String handle = handles.issue(saved.id(), expiresAt);
        return new RegistrationResult(snapshot(saved), provisioningToken, handle, expiresAt);
    }

    private AuthAccount resume(AuthAccount existing, RegisterCommand command, AuthAccount.Role role) {
        if (existing.userId() != null || existing.role() != role
                || !credentials.matches(command.password(), existing.passwordHash())) {
            throw new RegistrationIdentityConflict("Email already registered: " + command.email());
        }
        if (!Boolean.TRUE.equals(existing.isActive())) {
            throw new CredentialsRejected("Account is blocked or inactive");
        }
        return existing;
    }

    private static AccountSnapshot snapshot(AuthAccount a) {
        return new AccountSnapshot(a.id(), a.userId(), a.email(), a.role(), a.lifecycleStatus(), a.isActive(),
                a.emailVerificationRequired(), a.emailVerifiedAt());
    }
}
