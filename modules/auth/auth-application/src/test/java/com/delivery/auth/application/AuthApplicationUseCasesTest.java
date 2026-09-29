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
        DefaultRegistrationUseCase useCase = new DefaultRegistrationUseCase(fakes, fakes, fakes);

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
        DefaultRegistrationUseCase useCase = new DefaultRegistrationUseCase(fakes, fakes, fakes);
        fakes.account = activeAccount();

        assertThatThrownBy(() -> useCase.register(new RegisterCommand("USER@example.com", "secret", "USER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already exists");

        fakes.account = null;
        var result = useCase.register(new RegisterCommand(" USER@example.com ", "secret", "USER"));
        assertThat(result.account().email()).isEqualTo("user@example.com");
        assertThat(result.provisioningToken()).isEqualTo("provisioning-token");
        assertThat(fakes.saved.email()).isEqualTo("user@example.com");
    }

    @Test
    void loginRejectsMalformedOrIneligibleAccountBeforeSessionCreation() {
        Fakes fakes = new Fakes();
        DefaultLoginUseCase useCase = new DefaultLoginUseCase(fakes, fakes, fakes, fakes);

        assertThatThrownBy(() -> useCase.login(new LoginCommand(null, "secret", "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", null, "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "secret", null, "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "secret", " ", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "wrong", "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");
        fakes.account = activeAccount();
        fakes.passwordMatches = false;
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "secret", "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class);
        fakes.passwordMatches = true;
        fakes.account = new AuthAccount(7L, null, AuthAccount.LifecycleStatus.ACTIVE, 1L,
                "user@example.com", "hash", AuthAccount.Role.USER, true, false, null,
                false, 0L, null, null, 0, null, false, null, null, 0L, null, null, null);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("user@example.com", "secret", "device", "web", Session.DeviceType.WEB, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(fakes.sessionsSaved).isZero();
    }

    @Test
    void loginDeactivatesDeviceSessionAndIssuesBothTokens() {
        Fakes fakes = new Fakes();
        fakes.account = activeAccount();
        DefaultLoginUseCase useCase = new DefaultLoginUseCase(fakes, fakes, fakes, fakes);

        AuthenticationResult result = useCase.login(new LoginCommand(
                " USER@example.com ", "secret", "device-1", "Chrome", Session.DeviceType.WEB, "127.0.0.1"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(fakes.deactivatedDevice).isEqualTo("device-1");
        assertThat(fakes.sessionsSaved).isEqualTo(1);
        assertThat(fakes.savedSession.deviceId()).isEqualTo("device-1");
    }

    private static AuthAccount activeAccount() {
        return new AuthAccount(7L, 11L, AuthAccount.LifecycleStatus.ACTIVE, 1L,
                "user@example.com", "hash", AuthAccount.Role.USER, true, false, null,
                false, 0L, null, null, 0, null, false, null, null, 0L, null, null, null);
    }

    private static final class Fakes implements AuthAccountPort, PasswordCredentialPort, SessionPort, TokenPort {
        AuthAccount account;
        AuthAccount saved;
        Session savedSession;
        boolean passwordMatches = true;
        int lookups;
        int sessionsSaved;
        String deactivatedDevice;

        @Override public Optional<AuthAccount> findByEmail(String email) { lookups++; return Optional.ofNullable(account); }
        @Override public Optional<AuthAccount> findById(Long accountId) { return Optional.ofNullable(account); }
        @Override public AuthAccount save(AuthAccount account) { saved = account; return activeAccount(); }
        @Override public String hash(String rawPassword) { return "hashed:" + rawPassword; }
        @Override public boolean matches(String rawPassword, String passwordHash) { return passwordMatches && "secret".equals(rawPassword); }
        @Override public Session save(Session session) { savedSession = session; sessionsSaved++; return session; }
        @Override public List<Session> findByAccountAndDeviceForUpdate(Long accountId, String deviceId) { return new ArrayList<>(); }
        @Override public List<Session> findActiveByAccount(Long accountId, LocalDateTime now, int limit) { return List.of(); }
        @Override public int deactivateAllForAccount(Long accountId, LocalDateTime at) { return 0; }
        @Override public int deactivateForAccountAndDevice(Long accountId, String deviceId, LocalDateTime at) { deactivatedDevice = deviceId; return 1; }
        @Override public TokenValidity inspect(String rawToken) { return null; }
        @Override public String issueAccessToken(AuthAccount account) { return "access-token"; }
        @Override public String issueRefreshToken(AuthAccount account, String tokenFamilyId) { return "refresh-token"; }
        @Override public String issueProvisioningToken(AuthAccount account) { return "provisioning-token"; }
    }
}
