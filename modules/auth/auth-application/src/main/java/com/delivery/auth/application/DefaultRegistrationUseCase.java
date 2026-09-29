package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.policy.RegistrationPolicy;
import java.util.Objects;

/** Coordinates public registration while keeping policy and persistence behind ports. */
public final class DefaultRegistrationUseCase implements RegistrationUseCase {
    private final AuthAccountPort accounts;
    private final PasswordCredentialPort credentials;
    private final TokenPort tokens;

    public DefaultRegistrationUseCase(AuthAccountPort accounts, PasswordCredentialPort credentials, TokenPort tokens) {
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.tokens = Objects.requireNonNull(tokens);
    }

    @Override public RegistrationResult register(RegisterCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.email() == null || command.email().isBlank() || command.password() == null || command.password().isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }
        String email = command.email().trim().toLowerCase();
        if (accounts.findByEmail(email).isPresent()) throw new IllegalArgumentException("Email already exists");
        AuthAccount.Role role = RegistrationPolicy.parsePublicRole(command.role());
        AuthAccount account = new AuthAccount(null, null, AuthAccount.LifecycleStatus.PENDING_PROFILE, 0L, email,
                credentials.hash(command.password()), role, true, true, null, false, 0L, null, null, 0, null,
                false, null, null, 0L, null, null, null);
        AuthAccount saved = accounts.save(account);
        return new RegistrationResult(snapshot(saved), tokens.issueProvisioningToken(saved), null, null);
    }

    private static AccountSnapshot snapshot(AuthAccount a) {
        return new AccountSnapshot(a.id(), a.userId(), a.email(), a.role(), a.lifecycleStatus(), a.isActive(),
                a.emailVerificationRequired(), a.emailVerifiedAt());
    }
}
