package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.policy.*;
import java.time.LocalDateTime;
import java.util.Objects;

/** Rotation is atomic; reuse revocation commits before the rejection is reported. */
public final class DefaultRefreshTokenUseCase implements RefreshTokenUseCase, LogoutUseCase {
    private final AuthTransactionPort transactions;
    private final RefreshTokenVerificationPort verification;
    private final RefreshCredentialLockPort credentials;
    private final SessionTokenPort tokens;

    public DefaultRefreshTokenUseCase(AuthTransactionPort transactions, RefreshTokenVerificationPort verification,
            RefreshCredentialLockPort credentials, SessionTokenPort tokens) {
        this.transactions = Objects.requireNonNull(transactions);
        this.verification = Objects.requireNonNull(verification);
        this.credentials = Objects.requireNonNull(credentials);
        this.tokens = Objects.requireNonNull(tokens);
    }

    @Override public AuthenticationResult refresh(RefreshTokenCommand command) {
        Objects.requireNonNull(command, "command");
        Outcome outcome = transactions.required(() -> {
            requireValid(command.refreshToken());
            return credentials.withLocked(command.refreshToken(), found -> rotate(command.refreshToken(),
                    found.orElseThrow(() -> new InvalidAuthToken("Refresh token not found or expired"))));
        });
        if (outcome.reuseReason() != null) {
            throw new RefreshCredentialReuse(outcome.reuseReason());
        }
        return outcome.result();
    }

    private Outcome rotate(String raw, LockedRefreshCredential credential) {
        var facts = credential.facts();
        var session = facts.session();
        LocalDateTime now = LocalDateTime.now();
        if (facts.state() != com.delivery.auth.domain.model.RefreshCredentialState.CURRENT) {
            credential.revokeFamily(now);
            return new Outcome(null, "Refresh token reuse detected; device session revoked");
        }
        String claimedFamily = verification.claimedFamily(raw);
        if (claimedFamily != null && !claimedFamily.equals(session.tokenFamilyId())) {
            credential.revokeFamily(now);
            return new Outcome(null, "Refresh token family mismatch; device session revoked");
        }
        if (!session.isUsableAt(now)) {
            throw new InvalidAuthToken("Session is inactive");
        }
        AuthAccount account = facts.account();
        if (!Boolean.TRUE.equals(account.isActive())) {
            throw new InvalidAuthToken("Account is blocked or inactive");
        }
        if (Boolean.TRUE.equals(account.emailVerificationRequired()) && account.emailVerifiedAt() == null) {
            throw new InvalidAuthToken("Email verification required");
        }
        if (account.lifecycleStatus() != AuthAccount.LifecycleStatus.ACTIVE) {
            throw new CredentialsRejected("Account onboarding is not complete");
        }
        if (account.userId() == null) {
            throw new CredentialsRejected("Account profile is not provisioned");
        }
        String access = tokens.issueAccessToken(account);
        String refresh = tokens.issueRefreshToken(account, session.tokenFamilyId());
        LocalDateTime expires = now.plusDays(7);
        credential.markRotated(now);
        credential.rememberSuccessor(refresh, now, expires);
        credential.updateSessionActivity(now, expires);
        return new Outcome(new AuthenticationResult(access, refresh, account.id(), account.email(), account.role().name()), null);
    }

    @Override public void logout(String rawRefreshToken) {
        transactions.required(() -> {
            requireValid(rawRefreshToken);
            return credentials.withLocked(rawRefreshToken, found -> {
                found.ifPresent(credential -> credential.revokeFamily(LocalDateTime.now()));
                return null;
            });
        });
    }

    private void requireValid(String raw) {
        if (!verification.isValid(raw)) throw new InvalidAuthToken("Invalid refresh token");
    }

    private record Outcome(AuthenticationResult result, String reuseReason) {}
}
