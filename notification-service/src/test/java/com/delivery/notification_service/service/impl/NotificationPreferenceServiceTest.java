package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.entity.NotificationPreference;
import com.delivery.notification_service.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationPreferenceServiceTest {

    @Test
    void absentPreferenceDefaultsMarketingOffWhileTransactionalRemainsOn() {
        NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
        NotificationPreferenceServiceImpl service = service(repository, "jdbc:h2:mem:notification-preferences");
        when(repository.findById(71L)).thenReturn(Optional.empty());

        var response = service.getPreferences(71L);

        assertThat(response.isConfigured()).isFalse();
        assertThat(response.isMarketingNotificationsEnabled()).isFalse();
        assertThat(response.isTransactionalNotificationsEnabled()).isTrue();
    }

    @Test
    void updateUsesCanonicalPrincipalAndAtomicH2Upsert() {
        NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
        NotificationPreferenceServiceImpl service = service(repository, "jdbc:h2:mem:notification-preferences");
        NotificationPreference stored = new NotificationPreference();
        stored.setPrincipalId(71L);
        stored.setMarketingNotificationsEnabled(true);
        stored.setUpdatedAt(LocalDateTime.of(2026, 8, 23, 17, 30));
        when(repository.upsertH2(71L, true)).thenReturn(1);
        when(repository.findById(71L)).thenReturn(Optional.of(stored));

        var response = service.updateMarketingNotifications(71L, true);

        verify(repository).upsertH2(71L, true);
        assertThat(response.isConfigured()).isTrue();
        assertThat(response.isMarketingNotificationsEnabled()).isTrue();
        assertThat(response.isTransactionalNotificationsEnabled()).isTrue();
        assertThat(response.getUpdatedAt()).isEqualTo(stored.getUpdatedAt());
    }

    @Test
    void invalidPrincipalFailsBeforeRepositoryAccess() {
        NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
        NotificationPreferenceServiceImpl service = service(repository, "jdbc:postgresql://db/notification");

        assertThatThrownBy(() -> service.updateMarketingNotifications(0L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("principalId");

        verifyNoInteractions(repository);
    }

    private NotificationPreferenceServiceImpl service(NotificationPreferenceRepository repository, String url) {
        NotificationPreferenceServiceImpl service = new NotificationPreferenceServiceImpl(repository);
        ReflectionTestUtils.setField(service, "dataSourceUrl", url);
        return service;
    }
}
