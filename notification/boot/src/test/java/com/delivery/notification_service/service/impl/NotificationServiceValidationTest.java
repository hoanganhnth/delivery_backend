package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.FirebaseService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationServiceValidationTest {

    @Test
    void sendNotificationRejectsNullRequestBeforeRepositoryAccess() {
        NotificationRepository repository = mock(NotificationRepository.class);
        FirebaseService firebaseService = mock(FirebaseService.class);
        NotificationServiceImpl service = new NotificationServiceImpl(
                repository, new NotificationMapper(), firebaseService);

        assertThrows(IllegalArgumentException.class, () -> service.sendNotification(null));

        verifyNoInteractions(repository, firebaseService);
    }

    @Test
    void unreadCountRejectsInvalidUserIdBeforeRepositoryAccess() {
        NotificationRepository repository = mock(NotificationRepository.class);
        FirebaseService firebaseService = mock(FirebaseService.class);
        NotificationServiceImpl service = new NotificationServiceImpl(
                repository, new NotificationMapper(), firebaseService);

        assertThrows(IllegalArgumentException.class, () -> service.getUnreadCount(0L));

        verifyNoInteractions(repository, firebaseService);
    }
}
