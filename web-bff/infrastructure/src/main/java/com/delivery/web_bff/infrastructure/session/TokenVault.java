package com.delivery.web_bff.infrastructure.session;

import com.delivery.web_bff.domain.session.TokenProtection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM adapter; ciphertexts are versioned and authenticated with the key version as AAD. */
public final class TokenVault implements TokenProtection, com.delivery.web_bff.application.api.Ports.TokenProtection {
    private static final int NONCE_BYTES = 12;
    private final String version;
    private final byte[] key;
    private final SecureRandom random;

    public TokenVault(String version, byte[] key, SecureRandom random) {
        if (version == null || version.isBlank() || key == null || key.length != 32 || random == null)
            throw new IllegalArgumentException("Web BFF encryption key must be exactly 256 bits");
        this.version = version; this.key = key.clone(); this.random = random;
    }
    @Override public String keyVersion() { return version; }
    @Override public String protect(String plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("Token material is required");
        try {
            byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(version.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return version + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Unable to protect token", e); }
    }
    @Override public String reveal(String protectedValue) {
        try {
            if (protectedValue == null) throw new IllegalStateException("Protected token is missing");
            String[] parts = protectedValue.split("\\.", -1);
            if (parts.length != 2 || !version.equals(parts[0])) throw new IllegalStateException("Unknown token key version");
            byte[] packed = Base64.getUrlDecoder().decode(parts[1]);
            if (packed.length <= NONCE_BYTES + 16) throw new IllegalStateException("Invalid protected token");
            byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, NONCE_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(packed, NONCE_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(version.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw new IllegalStateException("Unable to reveal token", e); }
    }
}
