package com.delivery.notification.domain;

/** Existing push contract; preferences never gate transactional wake-ups. */
public final class PushEligibility {
    private PushEligibility() {}

    public static boolean requested(Boolean sendPush) { return Boolean.TRUE.equals(sendPush); }

    public static void validate(Long userId, String title, String body) {
        NotificationLifecycle.requirePositiveId(userId, "userId");
        requireText(title, "title");
        requireText(body, "body");
    }

    public static void validateToken(Long userId, String token) {
        NotificationLifecycle.requirePositiveId(userId, "userId");
        requireText(token, "fcmToken");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
