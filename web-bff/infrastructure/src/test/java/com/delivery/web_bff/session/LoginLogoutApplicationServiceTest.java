package com.delivery.web_bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.domain.session.*;
import com.delivery.web_bff.infrastructure.session.TokenVault;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoginLogoutApplicationServiceTest {
    private final Ports.Sessions repository = mock(Ports.Sessions.class);
    private final Ports.Authentication auth = mock(Ports.Authentication.class);
    private final TokenVault vault = new TokenVault("v1",
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), new SecureRandom());
    private final Instant now = Instant.parse("2026-09-15T00:00:00Z");
    private final SessionFactory factory = new SessionFactory(vault, size -> { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }, Duration.ofDays(7));
    private final com.delivery.web_bff.auth.LoginApplicationService login = new com.delivery.web_bff.auth.LoginApplicationService(auth, factory, repository, () -> now);
    private final LogoutApplicationService logout = new LogoutApplicationService(repository, auth, vault, () -> now);

    @Test
    void loginPersistsEncryptedTokensWithoutReturningThemAsSessionIdentity() {
        when(auth.login(org.mockito.ArgumentMatchers.any())).thenReturn(new Ports.AuthenticatedTokens(
                "access-secret", "refresh-secret", 42L, "user@example.com", "USER"));

        SessionMaterial material = login.execute(new Ports.LoginCommand("user@example.com", "password", "USER", "Browser", "device"));

        assertThat(material.session().principalId()).isEqualTo(42L);
        assertThat(material.session().accessTokenCipher()).doesNotContain("access-secret");
        assertThat(material.session().refreshTokenCipher()).doesNotContain("refresh-secret");
        verify(repository).save(material.session());
    }

    @Test
    void invalidCsrfCannotRevokeSessionOrCallAuthLogout() {
        SessionMaterial material = new SessionFactory(vault, size -> { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }, Duration.ofDays(7))
                .create("access", "refresh", 42L, "user@example.com", "USER", now);
        when(repository.mutate(org.mockito.ArgumentMatchers.eq(material.session().sessionHash()), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    java.util.function.Consumer<WebSession> transition = call.getArgument(1);
                    transition.accept(material.session());
                    return Optional.of(material.session());
                });

        assertThatThrownBy(() -> logout.execute(material.rawSessionId(), "wrong"))
                .isInstanceOf(SessionRejectedException.class);
        assertThat(material.session().revokedAt()).isNull();
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(auth, never()).logout(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void logoutRevokesLocallyEvenWhenAuthRevocationFails() {
        SessionMaterial material = new SessionFactory(vault, size -> { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }, Duration.ofDays(7))
                .create("access", "refresh", 42L, "user@example.com", "USER", now);
        when(repository.mutate(org.mockito.ArgumentMatchers.eq(material.session().sessionHash()), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    java.util.function.Consumer<WebSession> transition = call.getArgument(1);
                    transition.accept(material.session());
                    return Optional.of(material.session());
                });
        org.mockito.Mockito.doThrow(new IllegalStateException("upstream down")).when(auth).logout("refresh");

        logout.execute(material.rawSessionId(), material.rawCsrfToken());

        assertThat(material.session().revokedAt()).isEqualTo(now);
        assertThat(material.session().generation()).isEqualTo(2);
        verify(repository).mutate(org.mockito.ArgumentMatchers.eq(material.session().sessionHash()), org.mockito.ArgumentMatchers.any());
    }
}
