package com.delivery.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class TokenFingerprint {
    private TokenFingerprint() {}
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 is unavailable", unavailable); }
    }
}
