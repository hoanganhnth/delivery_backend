package com.delivery.web_bff.domain.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Security {
    private Security() { }
    public static String hash(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Secret is required");
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
}
