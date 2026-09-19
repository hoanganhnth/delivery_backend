package com.delivery.web_bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionFactoryTest {
    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void encryptsTokensAndStoresOnlyHashesForBrowserSecrets() {
        TokenVault vault = new TokenVault("v1", key, new SecureRandom());
        SessionMaterial material = new SessionFactory(vault, new SecureRandom(), Duration.ofDays(7))
                .create("access-secret", "refresh-secret", 42L, "user@example.com", "USER", Instant.EPOCH);

        assertThat(material.session().getSessionHash()).isEqualTo(SessionFactory.hash(material.rawSessionId()));
        assertThat(material.session().getCsrfHash()).isEqualTo(SessionFactory.hash(material.rawCsrfToken()));
        assertThat(material.session().getAccessTokenCipher()).doesNotContain("access-secret");
        assertThat(material.session().getRefreshTokenCipher()).doesNotContain("refresh-secret");
        assertThat(vault.decrypt(material.session().getAccessTokenCipher())).isEqualTo("access-secret");
        assertThat(vault.decrypt(material.session().getRefreshTokenCipher())).isEqualTo("refresh-secret");
        assertThat(material.session().getExpiresAt()).isEqualTo(Instant.EPOCH.plus(Duration.ofDays(7)));
    }

    @Test
    void rejectsWrongEncryptionKey() {
        TokenVault first = new TokenVault("v1", key, new SecureRandom());
        byte[] other = "abcdef0123456789abcdef0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TokenVault second = new TokenVault("v1", other, new SecureRandom());
        assertThatThrownBy(() -> second.decrypt(first.encrypt("refresh-secret")))
                .isInstanceOf(IllegalStateException.class);
    }
}
