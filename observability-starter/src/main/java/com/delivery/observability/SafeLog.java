package com.delivery.observability;

import java.util.regex.Pattern;

/** Redacts exception text before it reaches logs; request bodies are never logged. */
public final class SafeLog {
    private static final Pattern SECRET = Pattern.compile("(?i)(authorization|token|password|secret|cookie)\\s*[=:]\\s*[^,\\s]+");
    private static final int MAX_MESSAGE_LENGTH = 512;

    private SafeLog() { }

    public static String exceptionMessage(Throwable error) {
        String message = error == null || error.getMessage() == null ? "Unexpected failure" : error.getMessage();
        message = SECRET.matcher(message).replaceAll("$1=[REDACTED]");
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }
}
