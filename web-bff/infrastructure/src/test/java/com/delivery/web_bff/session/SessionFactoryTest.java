package com.delivery.web_bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.security.SecureRandom;
import com.delivery.web_bff.domain.session.*;
import com.delivery.web_bff.infrastructure.session.TokenVault;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionFactoryTest {
    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void encryptsTokensAndStoresOnlyHashesForBrowserSecrets() {
        TokenVault vault = new TokenVault("v1", key, new SecureRandom());
        SessionMaterial material = new SessionFactory(vault, size -> { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }, Duration.ofDays(7))
                .create("access-secret", "refresh-secret", 42L, "user@example.com", "USER", Instant.EPOCH);

        assertThat(material.session().sessionHash()).isEqualTo(Security.hash(material.rawSessionId()));
        assertThat(material.session().csrfHash()).isEqualTo(Security.hash(material.rawCsrfToken()));
        assertThat(material.session().accessTokenCipher()).doesNotContain("access-secret");
        assertThat(material.session().refreshTokenCipher()).doesNotContain("refresh-secret");
        assertThat(vault.reveal(material.session().accessTokenCipher())).isEqualTo("access-secret");
        assertThat(vault.reveal(material.session().refreshTokenCipher())).isEqualTo("refresh-secret");
        assertThat(material.session().expiresAt()).isEqualTo(Instant.EPOCH.plus(Duration.ofDays(7)));
    }

    @Test
    void rejectsWrongEncryptionKey() {
        TokenVault first = new TokenVault("v1", key, new SecureRandom());
        byte[] other = "abcdef0123456789abcdef0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TokenVault second = new TokenVault("v1", other, new SecureRandom());
        assertThatThrownBy(() -> second.reveal(first.protect("refresh-secret")))
                .isInstanceOf(IllegalStateException.class);
    }
}
