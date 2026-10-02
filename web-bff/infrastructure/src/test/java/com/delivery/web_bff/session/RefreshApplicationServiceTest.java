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

class RefreshApplicationServiceTest {
    private final Ports.Sessions repository = mock(Ports.Sessions.class);
    private final Ports.Authentication auth = mock(Ports.Authentication.class);
    private final TokenVault vault = new TokenVault("v1",
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), new SecureRandom());
    private final Instant now = Instant.parse("2026-09-15T00:00:00Z");
    private final SessionMaterial material = new SessionFactory(vault, size -> { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }, Duration.ofDays(7))
            .create("old-access", "old-refresh", 42L, "user@example.com", "USER", now);
    private final RefreshApplicationService service = new RefreshApplicationService(repository, auth, vault, () -> now);

    @Test
    void claimsRefreshThenAtomicallyRotatesEncryptedPair() {
        when(repository.active(material.session().sessionHash(), now)).thenReturn(Optional.of(material.session()));
        when(repository.claimRefresh(material.session().sessionHash(), 1, now, now.plusSeconds(30))).thenReturn(true);
        when(repository.mutate(org.mockito.ArgumentMatchers.eq(material.session().sessionHash()), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    java.util.function.Consumer<WebSession> transition = call.getArgument(1);
                    transition.accept(material.session());
                    return Optional.of(material.session());
                });
        when(auth.refresh("old-refresh")).thenReturn(new Ports.AuthenticatedTokens(
                "new-access", "new-refresh", 42L, "user@example.com", "USER"));

        service.execute(material.rawSessionId(), material.rawCsrfToken());
        WebSession refreshed = material.session();

        assertThat(refreshed.generation()).isEqualTo(2);
        assertThat(vault.reveal(refreshed.accessTokenCipher())).isEqualTo("new-access");
        assertThat(vault.reveal(refreshed.refreshTokenCipher())).isEqualTo("new-refresh");
        verify(auth).refresh("old-refresh");
    }

    @Test
    void losingClaimNeverCallsRotatingUpstream() {
        when(repository.active(material.session().sessionHash(), now)).thenReturn(Optional.of(material.session()));
        when(repository.claimRefresh(material.session().sessionHash(), 1, now, now.plusSeconds(30))).thenReturn(false);

        assertThatThrownBy(() -> service.execute(material.rawSessionId(), material.rawCsrfToken()))
                .isInstanceOf(RefreshInProgressException.class);
        verify(auth, never()).refresh(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownUpstreamOutcomeRevokesInsteadOfReusingRefreshToken() {
        when(repository.active(material.session().sessionHash(), now)).thenReturn(Optional.of(material.session()));
        when(repository.claimRefresh(material.session().sessionHash(), 1, now, now.plusSeconds(30))).thenReturn(true);
        when(repository.mutate(org.mockito.ArgumentMatchers.eq(material.session().sessionHash()), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    java.util.function.Consumer<WebSession> transition = call.getArgument(1);
                    transition.accept(material.session());
                    return Optional.of(material.session());
                });
        when(auth.refresh("old-refresh")).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service.execute(material.rawSessionId(), material.rawCsrfToken()))
                .isInstanceOf(SessionRejectedException.class);
        assertThat(material.session().revokedAt()).isEqualTo(now);
        verify(repository).mutate(org.mockito.ArgumentMatchers.eq(material.session().sessionHash()), org.mockito.ArgumentMatchers.any());
    }
}
