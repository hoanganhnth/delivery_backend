package com.delivery.notification_service.controller;

import com.delivery.notification_service.exception.NotificationAccessDeniedException;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationControllerAuthorizationTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationController controller = new NotificationController(notificationService, "secret");

    @Test
    void rejectsPathUserThatDoesNotMatchAuthenticatedUser() {
        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.getUserNotifications(42L, 99L, "USER"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void userScopedNotificationSurfacesRejectMissingOrUnknownRoles() {
        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.getUnreadNotifications(99L, null));
        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.deleteNotification(7L, 99L, "GUEST"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void shipperCanReadOwnUnreadNotificationsForOfferRecovery() {
        var response = controller.getUnreadNotifications(99L, "SHIPPER");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(notificationService).getUnreadNotifications(99L);
    }

    @Test
    void allKnownAuthenticatedRolesCanAccessOwnNotificationSurfaces() {
        controller.getUnreadCount(99L, "ADMIN");
        controller.markAsRead(7L, 99L, "SHIPPER");
        controller.markAllAsRead(99L, "SHOP_OWNER");
        controller.getNotificationById(7L, 99L, "USER");
        controller.deleteNotification(7L, 99L, "SHIPPER");

        verify(notificationService).getUnreadCount(99L);
        verify(notificationService).markAsRead(7L, 99L);
        verify(notificationService).markAllAsRead(99L);
        verify(notificationService).getNotificationById(7L, 99L);
        verify(notificationService).deleteNotification(7L, 99L);
    }

    @Test
    void manualSendRejectsMissingInternalCredential() {
        var response = controller.sendNotification(new SendNotificationRequest(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(notificationService);
    }

    @Test
    void manualSendAcceptsConfiguredInternalCredential() {
        SendNotificationRequest request = validRequest();

        var response = controller.sendNotification(request, "secret");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(notificationService).sendNotification(request);
    }

    private SendNotificationRequest validRequest() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(42L);
        request.setTitle("Order update");
        request.setMessage("Your order is ready");
        request.setType("ORDER_READY");
        return request;
    }
}
