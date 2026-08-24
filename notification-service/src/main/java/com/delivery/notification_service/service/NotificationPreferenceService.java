package com.delivery.notification_service.service;

import com.delivery.notification_service.dto.response.NotificationPreferenceResponse;

public interface NotificationPreferenceService {

    NotificationPreferenceResponse getPreferences(Long principalId);

    NotificationPreferenceResponse updateMarketingNotifications(Long principalId, boolean enabled);
}
