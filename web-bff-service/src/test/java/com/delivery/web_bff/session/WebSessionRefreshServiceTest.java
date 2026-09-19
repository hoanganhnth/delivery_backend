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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class WebSessionRefreshServiceTest {
    private final WebSessionRepository repository = mock(WebSessionRepository.class);
    private final AuthGateway auth = mock(AuthGateway.class);
    private final TokenVault vault = new TokenVault("v1",
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), new SecureRandom());
    private final Instant now = Instant.parse("2026-09-15T00:00:00Z");
    private final SessionMaterial material = new SessionFactory(vault, new SecureRandom(), Duration.ofDays(7))
            .create("old-access", "old-refresh", 42L, "user@example.com", "USER", now);
    private final WebSessionRefreshService service = new WebSessionRefreshService(repository, vault, auth,
            Clock.fixed(now, ZoneOffset.UTC), new TransactionTemplate(transactionManager()));

    private PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        };
    }

    @Test
    void claimsRefreshThenAtomicallyRotatesEncryptedPair() {
        when(repository.findActive(material.session().getSessionHash(), now)).thenReturn(Optional.of(material.session()));
        when(repository.claimRefresh(material.session().getSessionHash(), 1, now, now.plusSeconds(30))).thenReturn(1);
        when(repository.findById(material.session().getSessionHash())).thenReturn(Optional.of(material.session()));
        when(repository.save(material.session())).thenReturn(material.session());
        when(auth.refresh("old-refresh")).thenReturn(new AuthGateway.AuthTokens(
                "new-access", "new-refresh", 42L, "user@example.com", "USER"));

        WebSession refreshed = service.refresh(material.rawSessionId(), material.rawCsrfToken());

        assertThat(refreshed.getGeneration()).isEqualTo(2);
        assertThat(vault.decrypt(refreshed.getAccessTokenCipher())).isEqualTo("new-access");
        assertThat(vault.decrypt(refreshed.getRefreshTokenCipher())).isEqualTo("new-refresh");
        verify(auth).refresh("old-refresh");
    }

    @Test
    void losingClaimNeverCallsRotatingUpstream() {
        when(repository.findActive(material.session().getSessionHash(), now)).thenReturn(Optional.of(material.session()));
        when(repository.claimRefresh(material.session().getSessionHash(), 1, now, now.plusSeconds(30))).thenReturn(0);

        assertThatThrownBy(() -> service.refresh(material.rawSessionId(), material.rawCsrfToken()))
                .isInstanceOf(WebSessionRefreshService.RefreshRejectedException.class);
        verify(auth, never()).refresh(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownUpstreamOutcomeRevokesInsteadOfReusingRefreshToken() {
        when(repository.findActive(material.session().getSessionHash(), now)).thenReturn(Optional.of(material.session()));
        when(repository.claimRefresh(material.session().getSessionHash(), 1, now, now.plusSeconds(30))).thenReturn(1);
        when(repository.findById(material.session().getSessionHash())).thenReturn(Optional.of(material.session()));
        when(auth.refresh("old-refresh")).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service.refresh(material.rawSessionId(), material.rawCsrfToken()))
                .isInstanceOf(WebSessionService.SessionRejectedException.class);
        assertThat(material.session().getRevokedAt()).isEqualTo(now);
        verify(repository).save(material.session());
    }
}
