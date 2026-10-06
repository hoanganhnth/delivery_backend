package com.delivery.livestream.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class CheckoutFingerprint {
    private CheckoutFingerprint() { }
    public static String of(UUID room, Long restaurant, List<Long> products, Long actor) {
        String value = room + ":" + restaurant + ":" + products + ":" + actor;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
