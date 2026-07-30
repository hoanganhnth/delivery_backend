package com.delivery.observability;

import java.util.UUID;
import java.util.regex.Pattern;

/** Boundary validation for the single cross-service request identifier. */
public final class CorrelationId {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");

    private CorrelationId() { }

    public static String create() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String value) {
        return value != null && SAFE_VALUE.matcher(value).matches();
    }

    public static String requireValidOrCreate(String value) {
        if (value == null || value.isBlank()) {
            return create();
        }
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid X-Correlation-Id");
        }
        return value;
    }
}
