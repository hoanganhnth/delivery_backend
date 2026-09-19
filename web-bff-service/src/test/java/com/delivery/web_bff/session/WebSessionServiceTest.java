package com.delivery.web_bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.delivery.web_bff.auth.AuthGateway;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebSessionServiceTest {
    private final WebSessionRepository repository = mock(WebSessionRepository.class);
    private final AuthGateway auth = mock(AuthGateway.class);
    private final TokenVault vault = new TokenVault("v1",
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), new SecureRandom());
    private final Instant now = Instant.parse("2026-09-15T00:00:00Z");
    private final WebSessionService service = new WebSessionService(repository,
            new SessionFactory(vault, new SecureRandom(), Duration.ofDays(7)), vault, auth,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void loginPersistsEncryptedTokensWithoutReturningThemAsSessionIdentity() {
        when(auth.login(org.mockito.ArgumentMatchers.any())).thenReturn(new AuthGateway.AuthTokens(
                "access-secret", "refresh-secret", 42L, "user@example.com", "USER"));

        SessionMaterial material = service.login("user@example.com", "password", "USER", "Browser");

        assertThat(material.session().getPrincipalId()).isEqualTo(42L);
        assertThat(material.session().getAccessTokenCipher()).doesNotContain("access-secret");
        assertThat(material.session().getRefreshTokenCipher()).doesNotContain("refresh-secret");
        verify(repository).save(material.session());
    }

    @Test
    void invalidCsrfCannotRevokeSessionOrCallAuthLogout() {
        SessionMaterial material = new SessionFactory(vault, new SecureRandom(), Duration.ofDays(7))
                .create("access", "refresh", 42L, "user@example.com", "USER", now);
        when(repository.findActive(material.session().getSessionHash(), now))
                .thenReturn(Optional.of(material.session()));

        assertThatThrownBy(() -> service.logout(material.rawSessionId(), "wrong"))
                .isInstanceOf(WebSessionService.SessionRejectedException.class);
        assertThat(material.session().getRevokedAt()).isNull();
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(auth, never()).logout(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void logoutRevokesLocallyEvenWhenAuthRevocationFails() {
        SessionMaterial material = new SessionFactory(vault, new SecureRandom(), Duration.ofDays(7))
                .create("access", "refresh", 42L, "user@example.com", "USER", now);
        when(repository.findActive(material.session().getSessionHash(), now))
                .thenReturn(Optional.of(material.session()));
        org.mockito.Mockito.doThrow(new IllegalStateException("upstream down")).when(auth).logout("refresh");

        service.logout(material.rawSessionId(), material.rawCsrfToken());

        assertThat(material.session().getRevokedAt()).isEqualTo(now);
        assertThat(material.session().getGeneration()).isEqualTo(2);
        verify(repository).save(material.session());
    }
}
