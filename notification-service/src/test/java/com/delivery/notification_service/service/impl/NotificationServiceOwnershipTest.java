package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.exception.NotificationNotFoundException;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.FirebaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class NotificationServiceOwnershipTest {

    private NotificationRepository repository;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationServiceImpl(
                repository,
                mock(NotificationMapper.class),
                mock(FirebaseService.class));
    }

    @Test
    void getByIdRequiresOwnerMatch() {
        when(repository.findByIdAndUserId(7L, 42L)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class,
                () -> service.getNotificationById(7L, 42L));

        verify(repository).findByIdAndUserId(7L, 42L);
    }

    @Test
    void markAsReadScopesUpdateToOwner() {
        when(repository.markAsRead(eq(7L), eq(42L), any())).thenReturn(0);

        assertThrows(NotificationNotFoundException.class,
                () -> service.markAsRead(7L, 42L));

        verify(repository).markAsRead(eq(7L), eq(42L), any());
    }

    @Test
    void deleteScopesMutationToOwner() {
        when(repository.deleteByIdAndUserId(7L, 42L)).thenReturn(0L);

        assertThrows(NotificationNotFoundException.class,
                () -> service.deleteNotification(7L, 42L));

        verify(repository).deleteByIdAndUserId(7L, 42L);
    }

    @Test
    void repeatedMarkAsReadReturnsTheOwnedCurrentStateWithoutAnotherWrite() {
        Notification existing = new Notification();
        existing.setId(7L);
        existing.setUserId(42L);
        existing.setIsRead(true);
        when(repository.markAsRead(eq(7L), eq(42L), any())).thenReturn(0);
        when(repository.findByIdAndUserId(7L, 42L)).thenReturn(Optional.of(existing));
        service = new NotificationServiceImpl(
                repository, new NotificationMapper(), mock(FirebaseService.class));

        var response = service.markAsRead(7L, 42L);

        org.assertj.core.api.Assertions.assertThat(response.getIsRead()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void userAndUnreadListsUseBoundedQueriesWhileCountStaysExact() {
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(42L), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.findByUserIdAndIsReadOrderByCreatedAtDesc(
                eq(42L), eq(false), any(Pageable.class))).thenReturn(List.of());
        when(repository.countByUserIdAndIsRead(42L, false)).thenReturn(135L);

        service.getUserNotifications(42L);
        service.getUnreadNotifications(42L);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdOrderByCreatedAtDesc(eq(42L), page.capture());
        verify(repository).findByUserIdAndIsReadOrderByCreatedAtDesc(
                eq(42L), eq(false), any(Pageable.class));
        org.assertj.core.api.Assertions.assertThat(page.getValue().getPageSize()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(service.getUnreadCount(42L)).isEqualTo(135L);
    }
}
