package com.delivery.notification_service.service.impl;

import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.exception.NotificationNotFoundException;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import com.delivery.notification_service.service.FirebaseService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationInboxDelegationTest {
    private Notification row(Long principal) {
        var n=new Notification(); n.setId(9L); n.setUserId(2L); n.setUserPrincipalId(principal); return n;
    }
    @Test void principalAndFallbackQueriesRemainScopedAndBounded() {
        for(boolean enforced : new boolean[]{false,true}) {
            var repository=mock(NotificationRepository.class); var metrics=new SimpleMeterRegistry();
            var service=new NotificationServiceImpl(repository,new NotificationMapper(),new NotificationDeliveryCoordinator(repository,mock(FirebaseService.class)),metrics);
            ReflectionTestUtils.setField(service,"principalOwnershipEnforced",enforced);
            var rows=List.of(row(1L),row(null));
            if(enforced) {
                when(repository.findByUserPrincipalIdOrderByCreatedAtDesc(eq(1L),any())).thenReturn(rows);
                when(repository.findByUserPrincipalIdAndIsReadOrderByCreatedAtDesc(eq(1L),eq(false),any())).thenReturn(rows);
                when(repository.countByUserPrincipalIdAndIsRead(1L,false)).thenReturn(135L);
                when(repository.findByIdAndUserPrincipalId(9L,1L)).thenReturn(Optional.of(rows.get(0)));
            } else {
                when(repository.findByPrincipalOrUnmigratedLegacyUser(eq(1L),eq(2L),any())).thenReturn(rows);
                when(repository.findUnreadByPrincipalOrUnmigratedLegacyUser(eq(1L),eq(2L),eq(false),any())).thenReturn(rows);
                when(repository.countByPrincipalOrUnmigratedLegacyUserAndIsRead(1L,2L,false)).thenReturn(135L);
                when(repository.findByIdAndPrincipalOrUnmigratedLegacyUser(9L,1L,2L)).thenReturn(Optional.of(rows.get(0)));
            }
            assertEquals(2,service.getUserNotifications(1L,2L).size()); assertEquals(2,service.getUnreadNotifications(1L,2L).size());
            assertEquals(135,service.getUnreadCount(1L,2L)); assertEquals(9L,service.getNotificationById(9L,1L,2L).getId());
            assertEquals(2,service.markAllAsRead(1L,2L));
            assertTrue(rows.get(0).getIsRead()); assertEquals(rows.get(0).getReadAt(),rows.get(1).getReadAt());
            verify(repository).saveAll(rows);
            var captor=org.mockito.ArgumentCaptor.forClass(Pageable.class);
            if(enforced) {
                verify(repository).findByUserPrincipalIdOrderByCreatedAtDesc(eq(1L),captor.capture());
                verify(repository,never()).findByPrincipalOrUnmigratedLegacyUser(anyLong(),anyLong(),any());
                assertNull(metrics.find("delivery.identity.legacy.fallback").counter());
            } else {
                verify(repository).findByPrincipalOrUnmigratedLegacyUser(eq(1L),eq(2L),captor.capture());
                for(String surface : List.of("inbox_list","inbox_unread_list","inbox_mark_all_read"))
                    assertEquals(1,metrics.get("delivery.identity.legacy.fallback").tag("surface",surface).counter().count());
                verify(repository,never()).findByUserPrincipalIdOrderByCreatedAtDesc(anyLong(),any());
            }
            assertEquals(100,captor.getValue().getPageSize());
        }
    }
    @Test void fallbackSingleReadMetricsAndRepeatedReadTimestampArePreserved() {
        var repository=mock(NotificationRepository.class); var metrics=new SimpleMeterRegistry(); var n=row(null);
        var service=new NotificationServiceImpl(repository,new NotificationMapper(),new NotificationDeliveryCoordinator(repository,mock(FirebaseService.class)),metrics);
        when(repository.findByIdAndPrincipalOrUnmigratedLegacyUser(9L,1L,2L)).thenReturn(Optional.of(n));
        service.markAsRead(9L,1L,2L); var at=n.getReadAt(); assertNotNull(at);
        service.markAsRead(9L,1L,2L); assertEquals(at,n.getReadAt()); verify(repository,times(1)).save(n);
        service.getNotificationById(9L,1L,2L); service.deleteNotification(9L,1L,2L); verify(repository).delete(n);
        assertEquals(2,metrics.get("delivery.identity.legacy.fallback").tag("surface","inbox_mark_read").counter().count());
        assertEquals(1,metrics.get("delivery.identity.legacy.fallback").tag("surface","inbox_read").counter().count());
        assertEquals(1,metrics.get("delivery.identity.legacy.fallback").tag("surface","inbox_delete").counter().count());
    }
    @Test void missingPrincipalRowsPreserveNotFoundAndDoNotMutate() {
        for(boolean enforced : new boolean[]{true,false}) {
            var repository=mock(NotificationRepository.class); var service=new NotificationServiceImpl(repository,new NotificationMapper(),mock(FirebaseService.class));
            ReflectionTestUtils.setField(service,"principalOwnershipEnforced",enforced);
            assertThrows(NotificationNotFoundException.class,()->service.getNotificationById(9L,1L,2L));
            assertThrows(NotificationNotFoundException.class,()->service.markAsRead(9L,1L,2L));
            assertThrows(NotificationNotFoundException.class,()->service.deleteNotification(9L,1L,2L));
            verify(repository,never()).save(any()); verify(repository,never()).delete(any(Notification.class));
        }
    }
}
