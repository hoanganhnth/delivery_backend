package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.Session;
import com.delivery.auth.domain.policy.CredentialsRejected;
import java.time.LocalDateTime;
import java.util.*;

/** Provider verification, identity binding and session rotation share one transaction. */
public final class DefaultSocialLoginUseCase implements SocialLoginUseCase {
    private final AuthTransactionPort transactions;
    private final SocialIdentityPort identities;
    private final AuthAccountPort accounts;
    private final PasswordCredentialPort passwords;
    private final UserProfileProvisioningUseCase profiles;
    private final SessionPort sessions;
    private final SessionTokenPort tokens;
    private final RefreshCredentialPort refresh;

    public DefaultSocialLoginUseCase(AuthTransactionPort transactions, SocialIdentityPort identities,
            AuthAccountPort accounts, PasswordCredentialPort passwords, UserProfileProvisioningUseCase profiles,
            SessionPort sessions, SessionTokenPort tokens, RefreshCredentialPort refresh) {
        this.transactions=Objects.requireNonNull(transactions); this.identities=Objects.requireNonNull(identities);
        this.accounts=Objects.requireNonNull(accounts); this.passwords=Objects.requireNonNull(passwords);
        this.profiles=Objects.requireNonNull(profiles); this.sessions=Objects.requireNonNull(sessions);
        this.tokens=Objects.requireNonNull(tokens); this.refresh=Objects.requireNonNull(refresh);
    }
    @Override public AuthenticationResult login(SocialLoginCommand command) {
        Objects.requireNonNull(command, "command");
        return transactions.required(() -> loginInTransaction(command));
    }
    private AuthenticationResult loginInTransaction(SocialLoginCommand command) {
        if (!"google".equalsIgnoreCase(command.provider())) {
            throw new IllegalArgumentException("Unsupported provider: " + command.provider());
        }
        var identity=identities.verify(command.provider(),command.providerToken())
                .orElseThrow(() -> new CredentialsRejected("Invalid Google ID token"));
        if (!identity.emailVerified() || identity.email()==null || identity.email().isBlank()) {
            throw new CredentialsRejected("Google account email is not verified");
        }
        String email=identity.email().trim().toLowerCase(Locale.ROOT);
        AuthAccount account=accounts.findByEmail(email).orElseGet(() -> {
            String passwordHash=passwords.hash(UUID.randomUUID().toString());
            var role=command.requestedRole()==null || command.requestedRole().isBlank()
                    ? AuthAccount.Role.USER : PublicRegistrationRole.parse(command.requestedRole());
            var candidate=new AuthAccount(null,null,AuthAccount.LifecycleStatus.PENDING_PROFILE,0L,email,
                    passwordHash,role,true,false,LocalDateTime.now(),false,
                    0L,null,null,0,null,false,null,null,0L,null,null,null);
            // The database winner remains authoritative, including its operator-owned role.
            return accounts.createOrResume(candidate,winner -> {});
        });
        if (account.emailVerifiedAt()==null || Boolean.TRUE.equals(account.emailVerificationRequired())) {
            account=accounts.save(account.withVerifiedEmail(LocalDateTime.now()));
        }
        if (account.userId()==null) account=accounts.save(profiles.provision(account));
        // Preserve social eligibility: only an explicit false is rejected here.
        if (Boolean.FALSE.equals(account.isActive())) throw new CredentialsRejected("Account is blocked or inactive");
        if (account.userId()==null) throw new CredentialsRejected("Account profile is not provisioned");
        String device=command.deviceId()!=null && !command.deviceId().isBlank() ? command.deviceId() : "social-device";
        LocalDateTime revokedAt=LocalDateTime.now();
        refresh.revokeDevice(account.id(),device.trim(),revokedAt);
        sessions.deactivateForAccountAndDevice(account.id(),device.trim(),revokedAt);
        String access=tokens.issueAccessToken(account);
        String family=UUID.randomUUID().toString();
        String refreshToken=tokens.issueRefreshToken(account,family);
        LocalDateTime now=LocalDateTime.now();
        var session=sessions.save(new Session(null,account.id(),device,command.deviceName(),command.deviceType(),
                command.ipAddress(),family,true,now,now.plusDays(7),now));
        refresh.rememberCurrent(session,refreshToken,now,session.expiresAt());
        return new AuthenticationResult(access,refreshToken,account.id(),account.email(),account.role().name());
    }
}
