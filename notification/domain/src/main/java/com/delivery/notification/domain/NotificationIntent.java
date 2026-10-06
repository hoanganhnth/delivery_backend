package com.delivery.notification.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Transport-independent mapping result. Null data means no JSON payload. */
public record NotificationIntent(Long userId, Long userPrincipalId, String title, String message,
        String type, String priority, Long relatedEntityId, String relatedEntityType,
        String deduplicationKey, boolean sendPush, Map<String, Object> data) {
    public NotificationIntent {
        // Preserve null values and HashMap iteration used by the host's Gson serialization.
        if (data != null) {
            Map<String, Object> copy = new HashMap<>();
            copy.putAll(data);
            data = Collections.unmodifiableMap(copy);
        }
    }
}
