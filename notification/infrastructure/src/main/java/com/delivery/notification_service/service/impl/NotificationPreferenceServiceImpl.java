package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.dto.response.NotificationPreferenceResponse;
import com.delivery.notification.application.Preferences;
import com.delivery.notification.application.api.PreferencePort;
import java.util.Optional;
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
        return response(preferences().get(principalId));
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse updateMarketingNotifications(Long principalId, boolean enabled) {
        return response(preferences().update(principalId, enabled));
    }

    private Preferences preferences() {
        return new Preferences(
                new PreferencePort() {
                    public Optional<Stored> find(Long id) {
                        return repository.findById(id).map(p -> new Stored(p.isMarketingNotificationsEnabled(), p.getUpdatedAt()));
                    }
                    public int upsert(Long id, boolean enabled) {
                        return dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")
                                ? repository.upsertH2(id, enabled) : repository.upsertPostgres(id, enabled);
                    }
                });
    }

    private NotificationPreferenceResponse response(Preferences.Result result) {
        var preferences = result.preferences();
        return NotificationPreferenceResponse.builder()
                .transactionalNotificationsEnabled(preferences.transactionalNotificationsEnabled())
                .marketingNotificationsEnabled(preferences.marketingNotificationsEnabled())
                .configured(preferences.configured())
                .updatedAt(result.updatedAt())
                .build();
    }
}
