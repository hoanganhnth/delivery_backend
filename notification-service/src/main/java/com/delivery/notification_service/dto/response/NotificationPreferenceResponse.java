package com.delivery.notification_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Safe self-service projection; no legacy profile identity is exposed. */
@Getter
@Builder
public class NotificationPreferenceResponse {
    private final boolean transactionalNotificationsEnabled;
    private final boolean marketingNotificationsEnabled;
    private final boolean configured;
    private final LocalDateTime updatedAt;
}
