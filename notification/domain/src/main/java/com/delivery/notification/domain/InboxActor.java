package com.delivery.notification.domain;

/** Legacy entrypoints remain supported; principal mode requires both identifiers. */
public record InboxActor(Long principalId, Long legacyUserId, boolean legacyOnly, boolean enforced) {
    public void validate() {
        if (legacyOnly) NotificationLifecycle.requirePositiveId(legacyUserId, "userId");
        else {
            NotificationLifecycle.requirePositiveId(principalId, "principalId");
            NotificationLifecycle.requirePositiveId(legacyUserId, "legacyUserId");
        }
    }

    public boolean recordFallback() { return !legacyOnly && !enforced; }
    public static boolean needsRead(Boolean isRead) { return !Boolean.TRUE.equals(isRead); }
    public static int listLimit() { return 100; }
}
