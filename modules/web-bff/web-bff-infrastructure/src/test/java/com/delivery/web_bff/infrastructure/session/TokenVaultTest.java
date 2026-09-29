package com.delivery.web_bff.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class TokenVaultTest {
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test void roundTripUsesVersionedAuthenticatedCiphertext() {
        TokenVault vault = new TokenVault("v1", KEY, new SecureRandom());
        String protectedValue = vault.protect("refresh-secret");
        assertThat(protectedValue).startsWith("v1.").doesNotContain("refresh-secret");
        assertThat(vault.reveal(protectedValue)).isEqualTo("refresh-secret");
        assertThatThrownBy(() -> vault.reveal(protectedValue.substring(0, protectedValue.length() - 1) + "x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsNon256BitKeys() {
        assertThatThrownBy(() -> new TokenVault("v1", new byte[16], new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
