package com.delivery.web_bff.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

public class SessionFactory {
    private final TokenVault vault;
    private final SecureRandom random;
    private final Duration maxAge;

    public SessionFactory(TokenVault vault, SecureRandom random, Duration maxAge) {
        this.vault = vault; this.random = random; this.maxAge = maxAge;
    }

    public SessionMaterial create(String accessToken, String refreshToken, Long principalId,
            String email, String role, Instant now) {
        String rawSessionId = randomToken();
        String csrf = randomToken();
        WebSession session = new WebSession(hash(rawSessionId), vault.encrypt(accessToken), vault.encrypt(refreshToken),
                vault.version(), principalId, email, role, hash(csrf), 1, now.plus(maxAge), now);
        return new SessionMaterial(rawSessionId, csrf, session);
    }

    public static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
