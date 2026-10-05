package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.dto.response.NotificationPreferenceResponse;
import com.delivery.notification.domain.NotificationPreferences;
import com.delivery.notification_service.entity.NotificationPreference;
import com.delivery.notification_service.repository.NotificationPreferenceRepository;
import com.delivery.notification_service.service.NotificationPreferenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A missing row deliberately means marketing opt-out. The service keys only on
 * the canonical authentication principal and never falls back to a mutable
 * profile identifier.
 */
@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    public NotificationPreferenceServiceImpl(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(Long principalId) {
        requirePrincipal(principalId);
        return repository.findById(principalId).map(this::response)
                .orElseGet(NotificationPreferenceServiceImpl::defaultResponse);
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse updateMarketingNotifications(Long principalId, boolean enabled) {
        requirePrincipal(principalId);
        int changed = isH2()
                ? repository.upsertH2(principalId, enabled)
                : repository.upsertPostgres(principalId, enabled);
        if (changed != 1) {
            throw new IllegalStateException("notification preference update did not affect one principal");
        }
        NotificationPreference preference = repository.findById(principalId)
                .orElseThrow(() -> new IllegalStateException(
                        "notification preference upsert resolved without a committed row"));
        return response(preference);
    }

    private boolean isH2() {
        return dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:");
    }

    private static NotificationPreferenceResponse defaultResponse() {
        NotificationPreferences defaults = NotificationPreferences.fromStoredMarketing(null);
        return NotificationPreferenceResponse.builder()
                .transactionalNotificationsEnabled(defaults.transactionalNotificationsEnabled())
                .marketingNotificationsEnabled(defaults.marketingNotificationsEnabled())
                .configured(defaults.configured())
                .updatedAt(null)
                .build();
    }

    private NotificationPreferenceResponse response(NotificationPreference preference) {
        NotificationPreferences stored = NotificationPreferences.fromStoredMarketing(preference.isMarketingNotificationsEnabled());
        return NotificationPreferenceResponse.builder()
                .transactionalNotificationsEnabled(stored.transactionalNotificationsEnabled())
                .marketingNotificationsEnabled(stored.marketingNotificationsEnabled())
                .configured(stored.configured())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }

    private void requirePrincipal(Long principalId) {
        if (principalId == null || principalId <= 0) {
            throw new IllegalArgumentException("principalId must be positive");
        }
    }
}
