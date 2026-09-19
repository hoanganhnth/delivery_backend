package com.delivery.web_bff.session;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class TokenVault {
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec key;
    private final String version;
    private final SecureRandom random;

    public TokenVault(String version, byte[] key, SecureRandom random) {
        if (key == null || key.length != 32) throw new IllegalArgumentException("BFF encryption key must be 256 bits");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("BFF encryption key version is required");
        this.key = new SecretKeySpec(key.clone(), "AES"); this.version = version; this.random = random;
    }

    public String version() { return version; }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("Token is required");
        try {
            byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(version.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot encrypt session token", exception);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encoded);
            if (payload.length <= NONCE_BYTES) throw new IllegalArgumentException("Invalid encrypted token");
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, NONCE_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(version.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot decrypt session token", exception);
        }
    }
}
