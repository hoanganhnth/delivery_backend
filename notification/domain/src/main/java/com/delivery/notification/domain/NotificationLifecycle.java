package com.delivery.notification.domain;

import java.util.HashMap;
import java.util.Map;

/** Existing send/replay/delivery rules, independent of storage and transports. */
public final class NotificationLifecycle {
    private NotificationLifecycle() {}

    public static void validate(ReplayPayload payload) {
        if (payload == null) throw new IllegalArgumentException("Send notification request is required");
        requirePositiveId(payload.userId(), "userId");
        requireText(payload.title(), "title");
        requireText(payload.message(), "message");
        requireText(payload.type(), "type");
        requireText(payload.priority(), "priority");
    }

    public static void requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public static boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }

    public static boolean retryPending(String status) {
        return "PENDING".equals(status);
    }

    /** Must be evaluated against the locked row, never the pre-lock snapshot. */
    public static boolean shouldDeliver(Long id, String status) {
        if ("SENT".equals(status)) return false;
        if (!retryPending(status)) throw new IllegalStateException(
                "Notification " + id + " is not deliverable from status " + status);
        return true;
    }

    public static Map<String, String> pushData(Long id, ReplayPayload payload) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", id.toString());
        data.put("type", payload.type());
        if (payload.relatedEntityId() != null) {
            data.put("relatedEntityId", payload.relatedEntityId().toString());
            data.put("relatedEntityType", payload.relatedEntityType());
        }
        return data;
    }
}
