package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.Session;
import com.delivery.auth.domain.model.TokenValidity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthApplicationUseCasesTest {

    @Test
    void registrationRejectsMissingCredentialsBeforePorts() {
        Fakes fakes = new Fakes();
        DefaultRegistrationUseCase useCase = new DefaultRegistrationUseCase(fakes, fakes, fakes, fakes);

        assertThatThrownBy(() -> useCase.register(new RegisterCommand(null, "secret", "USER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register(new RegisterCommand("user@example.com", null, "USER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register(new RegisterCommand(" ", "secret", "USER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email and password are required");
        assertThatThrownBy(() -> useCase.register(new RegisterCommand("user@example.com", " ", "USER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(fakes.lookups).isZero();
    }

    @Test
    void registrationRejectsExistingEmailAndNormalizesNewEmail() {
        Fakes fakes = new Fakes();
        DefaultRegistrationUseCase useCase = new DefaultRegistrationUseCase(fakes, fakes, fakes, fakes);
        fakes.account = activeAccount();

        assertThatThrownBy(() -> useCase.register(new RegisterCommand("USER@example.com", "secret", "USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.RegistrationIdentityConflict.class)
                .hasMessage("Email already registered: USER@example.com");

        fakes.account = null;
        var result = useCase.register(new RegisterCommand(" USER@example.com ", "secret", "USER"));
        assertThat(result.account().email()).isEqualTo("user@example.com");
        assertThat(result.provisioningToken()).isEqualTo("provisioning-token");
        assertThat(fakes.saved.email()).isEqualTo("user@example.com");
    }

    @Test
    void registrationResumesMatchingPendingIdentityWithoutAnotherInsert() {
        Fakes fakes = new Fakes();
        fakes.account = new AuthAccount(7L, null, AuthAccount.LifecycleStatus.PENDING_PROFILE, 0L,
                "user@example.com", "hash", AuthAccount.Role.USER, true, true, null,
                false, 0L, null, null, 0, null, false, null, null, 0L, null, null, null);
        var result = new DefaultRegistrationUseCase(fakes, fakes, fakes, fakes)
                .register(new RegisterCommand("USER@example.com", "secret", "USER"));
        assertThat(result.account().id()).isEqualTo(7L);
        assertThat(result.account().userId()).isNull();
        assertThat(fakes.saved).isNull();
    }

    @Test
    void registrationRaceChecksWinnerIdentityAndActiveStateWithoutAnotherInsert() {
        Fakes fakes = new Fakes();
        fakes.winner = pendingAccount(AuthAccount.Role.USER, true);
        var useCase = new DefaultRegistrationUseCase(fakes, fakes, fakes, fakes);
        var result = useCase.register(new RegisterCommand("user@example.com", "secret", "USER"));
        assertThat(result.account().id()).isEqualTo(7L);
        assertThat(result.account().userId()).isNull();
        fakes.winner = pendingAccount(AuthAccount.Role.SHOP_OWNER, true);
        assertThatThrownBy(() -> useCase.register(new RegisterCommand("user@example.com", "secret", "USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.RegistrationIdentityConflict.class);
        fakes.winner = pendingAccount(AuthAccount.Role.USER, false);
        assertThatThrownBy(() -> useCase.register(new RegisterCommand("user@example.com", "secret", "USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class)
                .hasMessage("Account is blocked or inactive");
        fakes.winner = pendingAccount(AuthAccount.Role.USER, null);
        assertThatThrownBy(() -> useCase.register(new RegisterCommand("user@example.com", "secret", "USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class);
        fakes.winner = pendingAccount(AuthAccount.Role.USER, true);
        fakes.passwordMatches = false;
        assertThatThrownBy(() -> useCase.register(new RegisterCommand("user@example.com", "secret", "USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.RegistrationIdentityConflict.class);
    }

    @Test
    void publicRegistrationPreservesProductionRoleAdmission() {
        Fakes fakes = new Fakes();
        var useCase = new DefaultRegistrationUseCase(fakes, fakes, fakes, fakes);
        assertThatThrownBy(() -> useCase.register(null)).isInstanceOf(NullPointerException.class);
        for (String role : new String[]{null, " "}) {
            assertThatThrownBy(() -> useCase.register(new RegisterCommand("a@example.com", "secret", role)))
                    .hasMessage("Role is required");
        }
        for (String role : new String[]{" USER ", "moderator"}) {
            assertThatThrownBy(() -> useCase.register(new RegisterCommand("a@example.com", "secret", role)))
                    .hasMessage("Invalid role: " + role);
        }
        assertThatThrownBy(() -> useCase.register(new RegisterCommand("a@example.com", "secret", "ADMIN")))
                .hasMessage("ADMIN accounts cannot be self-registered");
        assertThatThrownBy(() -> useCase.register(new RegisterCommand("a@example.com", "secret", "SHIPPER")))
                .hasMessage("SHIPPER accounts require operator provisioning and profile onboarding");
        useCase.register(new RegisterCommand("USER@example.com", "secret", "CUSTOMER"));
        assertThat(fakes.saved.role()).isEqualTo(AuthAccount.Role.USER);
        useCase.register(new RegisterCommand("owner@example.com", "secret", "shop_owner"));
        assertThat(fakes.saved.role()).isEqualTo(AuthAccount.Role.SHOP_OWNER);
    }

    private static AuthAccount pendingAccount(AuthAccount.Role role, Boolean active) {
        return new AuthAccount(7L, null, AuthAccount.LifecycleStatus.PENDING_PROFILE, 0L,
                "user@example.com", "hash", role, active, true, null,
                false, 0L, null, null, 0, null, false, null, null, 0L, null, null, null);
    }

    @Test
    void loginRejectsMalformedOrIneligibleAccountBeforeSessionCreation() {
        Fakes fakes = new Fakes();
        fakes.account = activeAccount();
        DefaultLoginUseCase useCase = new DefaultLoginUseCase(fakes, fakes, fakes, fakes, fakes, fakes);

        assertThatThrownBy(() -> useCase.login(new LoginCommand(null, "secret", "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", null, "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "secret", null, "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "secret", " ", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "wrong", "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class)
                .hasMessage("Invalid email or password");
        fakes.account = activeAccount();
        fakes.passwordMatches = false;
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "secret", "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class);
        fakes.passwordMatches = true;
        fakes.account = new AuthAccount(7L, null, AuthAccount.LifecycleStatus.ACTIVE, 1L,
                "user@example.com", "hash", AuthAccount.Role.USER, true, false, null,
                false, 0L, null, null, 0, null, false, null, null, 0L, null, null, null);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "secret", "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class);
        assertThat(fakes.sessionsSaved).isZero();
    }

    @Test
    void loginDeactivatesDeviceSessionAndIssuesBothTokens() {
        Fakes fakes = new Fakes();
        fakes.account = activeAccount();
        DefaultLoginUseCase useCase = new DefaultLoginUseCase(fakes, fakes, fakes, fakes, fakes, fakes);

        AuthenticationResult result = useCase.login(new LoginCommand(
                " USER@example.com ", "secret", "device-1", "Chrome", Session.DeviceType.WEB, "127.0.0.1"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(fakes.deactivatedDevice).isEqualTo("device-1");
        assertThat(fakes.sessionsSaved).isEqualTo(1);
        assertThat(fakes.savedSession.deviceId()).isEqualTo("device-1");
    }

    @Test
    void loginRetainsStoredDeviceIdAndPersistsTheIssuedRefreshCredential() {
        Fakes fakes = new Fakes();
        fakes.account = activeAccount();
        var useCase = new DefaultLoginUseCase(fakes, fakes, fakes, fakes, fakes, fakes);
        useCase.login(new LoginCommand("user@example.com", "secret", " device-1 ", "web", Session.DeviceType.WEB, null));
        assertThat(fakes.deactivatedDevice).isEqualTo("device-1");
        assertThat(fakes.savedSession.deviceId()).isEqualTo(" device-1 ");
        assertThat(fakes.rememberedRefresh).isEqualTo("refresh-token");
    }

    @Test
    void loginPreservesEligibilityReasonsBeforeAnyTokenOrSessionMutation() {
        Fakes fakes = new Fakes();
        var useCase = new DefaultLoginUseCase(fakes, fakes, fakes, fakes, fakes, fakes);
        for (Boolean active : new Boolean[]{null, false}) {
            fakes.account = loginAccount(active, false, null, AuthAccount.LifecycleStatus.ACTIVE, 11L);
            assertThatThrownBy(() -> useCase.login(loginCommand()))
                    .hasMessage("Account is blocked or inactive");
        }
        fakes.account = loginAccount(true, true, null, AuthAccount.LifecycleStatus.ACTIVE, 11L);
        assertThatThrownBy(() -> useCase.login(loginCommand())).hasMessage("Email verification required");
        fakes.account = loginAccount(true, false, null, AuthAccount.LifecycleStatus.PENDING_PROFILE, 11L);
        assertThatThrownBy(() -> useCase.login(loginCommand())).hasMessage("Account onboarding is not complete");
        fakes.account = loginAccount(true, false, null, AuthAccount.LifecycleStatus.ACTIVE, null);
        assertThatThrownBy(() -> useCase.login(loginCommand())).hasMessage("Account profile is not provisioned");
        fakes.account = null;
        assertThatThrownBy(() -> useCase.login(loginCommand())).hasMessage("Invalid email or password");
        assertThat(fakes.sessionsSaved).isZero();
        assertThat(fakes.rememberedRefresh).isNull();
        assertThatThrownBy(() -> useCase.login(null)).isInstanceOf(NullPointerException.class);
        fakes.account = loginAccount(true, true, LocalDateTime.now(), AuthAccount.LifecycleStatus.ACTIVE, 11L);
        useCase.login(loginCommand());
        assertThat(fakes.operations).containsExactly("revoke", "deactivate", "access", "refresh", "session", "remember");
        assertThat(fakes.rememberedSession).isSameAs(fakes.savedSession);
        assertThat(fakes.rememberedExpiry).isEqualTo(fakes.savedSession.expiresAt());
    }

    private LoginCommand loginCommand() {
        return new LoginCommand("user@example.com", "secret", "device", "web", Session.DeviceType.WEB, null);
    }
    private static AuthAccount loginAccount(Boolean active, Boolean verificationRequired, LocalDateTime verified,
            AuthAccount.LifecycleStatus status, Long linkedUser) {
        return new AuthAccount(7L, linkedUser, status, 1L, "user@example.com", "hash", AuthAccount.Role.USER,
                active, verificationRequired, verified, false, 0L, null, null, 0, null,
                false, null, null, 0L, null, null, null);
    }

    private static AuthAccount activeAccount() {
        return new AuthAccount(7L, 11L, AuthAccount.LifecycleStatus.ACTIVE, 1L,
                "user@example.com", "hash", AuthAccount.Role.USER, true, false, null,
                false, 0L, null, null, 0, null, false, null, null, 0L, null, null, null);
    }

    private static final class Fakes implements AuthAccountPort, PasswordCredentialPort, SessionPort, TokenPort, RegistrationHandlePort, AuthTransactionPort, RefreshCredentialPort {
        AuthAccount account;
        AuthAccount winner;
        AuthAccount saved;
        Session savedSession;
        boolean passwordMatches = true;
        int lookups;
        int sessionsSaved;
        String deactivatedDevice;
        String rememberedRefresh;
        Session rememberedSession;
        LocalDateTime rememberedExpiry;
        boolean transactionOpen;
        List<String> operations = new ArrayList<>();

        @Override public <T> T required(java.util.function.Supplier<T> operation) {
            transactionOpen = true;
            try { return operation.get(); } finally { transactionOpen = false; }
        }
        @Override public void revokeDevice(Long accountId, String deviceId, LocalDateTime revokedAt) {
            assertThat(transactionOpen).isTrue(); operations.add("revoke");
        }
        @Override public void rememberCurrent(Session session, String rawToken, LocalDateTime issuedAt, LocalDateTime expiresAt) {
            assertThat(transactionOpen).isTrue(); operations.add("remember");
            rememberedRefresh = rawToken; rememberedSession = session; rememberedExpiry = expiresAt;
        }
        @Override public Optional<AuthAccount> findByEmail(String email) { lookups++; return Optional.ofNullable(account); }
        @Override public Optional<AuthAccount> findById(Long accountId) { return Optional.ofNullable(account); }
        @Override public AuthAccount save(AuthAccount account) { saved = account; return activeAccount(); }
        @Override public AuthAccount createOrResume(AuthAccount account, java.util.function.Consumer<AuthAccount> verifyWinner) {
            if (winner != null) { verifyWinner.accept(winner); return winner; }
            return save(account);
        }
        @Override public String hash(String rawPassword) { return "hashed:" + rawPassword; }
        @Override public boolean matches(String rawPassword, String passwordHash) { return passwordMatches && "secret".equals(rawPassword); }
        @Override public Session save(Session session) { assertThat(transactionOpen).isTrue(); operations.add("session"); savedSession = session; sessionsSaved++; return session; }
        @Override public List<Session> findByAccountAndDeviceForUpdate(Long accountId, String deviceId) { return new ArrayList<>(); }
        @Override public List<Session> findActiveByAccount(Long accountId, LocalDateTime now, int limit) { return List.of(); }
        @Override public int deactivateAllForAccount(Long accountId, LocalDateTime at) { return 0; }
        @Override public int deactivateForAccountAndDevice(Long accountId, String deviceId, LocalDateTime at) { assertThat(transactionOpen).isTrue(); operations.add("deactivate"); deactivatedDevice = deviceId; return 1; }
        @Override public TokenValidity inspect(String rawToken) { return null; }
        @Override public String issueAccessToken(AuthAccount account) { assertThat(transactionOpen).isTrue(); operations.add("access"); return "access-token"; }
        @Override public String issueRefreshToken(AuthAccount account, String tokenFamilyId) { assertThat(transactionOpen).isTrue(); operations.add("refresh"); return "refresh-token"; }
        @Override public String issueProvisioningToken(AuthAccount account) { return "provisioning-token"; }
        @Override public String issue(Long accountId, LocalDateTime expiresAt) { return "registration-handle"; }
    }
}
