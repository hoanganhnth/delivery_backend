package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.policy.AuthResourceMissing;
import java.util.Locale;
import java.util.Optional;

public final class DefaultAccountLookupUseCase implements AccountLookupUseCase {
    private final AuthAccountPort accounts;
    public DefaultAccountLookupUseCase(AuthAccountPort accounts) { this.accounts=accounts; }
    @Override public Optional<AccountSnapshot> byId(Long id) {
        return accounts.findById(id).map(DefaultAccountLookupUseCase::snapshot);
    }
    @Override public AccountSnapshot requireById(Long id) {
        return byId(id).orElseThrow(() -> new AuthResourceMissing("Account not found with id: " + id));
    }
    @Override public Optional<AccountSnapshot> byEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return accounts.findByEmail(normalizeEmail(email)).map(DefaultAccountLookupUseCase::snapshot);
    }
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private static AccountSnapshot snapshot(AuthAccount a) {
        return new AccountSnapshot(a.id(),a.userId(),a.email(),a.role(),a.lifecycleStatus(),a.isActive(),
                a.emailVerificationRequired(),a.emailVerifiedAt());
    }
}
