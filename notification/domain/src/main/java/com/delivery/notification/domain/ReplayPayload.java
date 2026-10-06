package com.delivery.notification.domain;

/** Exact immutable replay identity; sendPush and lifecycle state were never part of the comparison. */
public record ReplayPayload(Long userId, Long userPrincipalId, String title, String message,
        String type, String priority, Long relatedEntityId, String relatedEntityType, String data) {
    public boolean matches(ReplayPayload other) {
        return equals(other);
    }
}
