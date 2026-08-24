package com.delivery.notification_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.notification_service.exception.NotificationAccessDeniedException;
import com.delivery.notification_service.dto.request.SendNotificationRequest;
import com.delivery.notification_service.service.NotificationService;
import com.delivery.notification_service.service.NotificationPreferenceService;
import com.delivery.notification_service.dto.request.UpdateMarketingNotificationPreferenceRequest;
import org.junit.jupiter.api.Test;

import java.util.Set;

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
        AuthenticatedActor actor = new AuthenticatedActor(99L, "user@example.com", Set.of("USER"));
        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.getUserNotifications(42L, actor));

        verifyNoInteractions(notificationService);
    }

    @Test
    void userScopedNotificationSurfacesRejectMissingOrUnknownRoles() {
        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.getUnreadNotifications(null));
        AuthenticatedActor guestActor = new AuthenticatedActor(99L, "guest@example.com", Set.of("GUEST"));
        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.deleteNotification(7L, guestActor));

        verifyNoInteractions(notificationService);
    }

    @Test
    void shipperCanReadOwnUnreadNotificationsForOfferRecovery() {
        AuthenticatedActor actor = new AuthenticatedActor(99L, "shipper@example.com", Set.of("SHIPPER"));
        var response = controller.getUnreadNotifications(actor);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(notificationService).getUnreadNotifications(99L);
    }

    @Test
    void allKnownAuthenticatedRolesCanAccessOwnNotificationSurfaces() {
        AuthenticatedActor adminActor = new AuthenticatedActor(99L, "admin@example.com", Set.of("ADMIN"));
        AuthenticatedActor shipperActor = new AuthenticatedActor(99L, "shipper@example.com", Set.of("SHIPPER"));
        AuthenticatedActor shopActor = new AuthenticatedActor(99L, "shop@example.com", Set.of("SHOP_OWNER"));
        AuthenticatedActor userActor = new AuthenticatedActor(99L, "user@example.com", Set.of("USER"));

        controller.getUnreadCount(adminActor);
        controller.markAsRead(7L, shipperActor);
        controller.markAllAsRead(shopActor);
        controller.getNotificationById(7L, userActor);
        controller.deleteNotification(7L, shipperActor);

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

    @Test
    void preferenceReadAndMarketingUpdateArePrincipalScopedAndNeverToggleTransactionalDelivery() {
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        NotificationController controller = new NotificationController(
                notificationService, "secret", preferences, true);
        AuthenticatedActor actor = new AuthenticatedActor(77L, 42L, "user@example.com", Set.of("USER"));
        UpdateMarketingNotificationPreferenceRequest request = new UpdateMarketingNotificationPreferenceRequest();
        request.setMarketingNotificationsEnabled(true);

        assertThat(controller.getPreferences(actor).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(controller.updateMarketingPreference(request, actor).getStatusCode().is2xxSuccessful()).isTrue();

        verify(preferences).getPreferences(77L);
        verify(preferences).updateMarketingNotifications(77L, true);
    }

    @Test
    void preferenceCapabilityFailsClosedWhenDisabled() {
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        NotificationController controller = new NotificationController(
                notificationService, "secret", preferences, false);
        AuthenticatedActor actor = new AuthenticatedActor(77L, "user@example.com", Set.of("USER"));

        assertThat(controller.getPreferences(actor).getStatusCode().value()).isEqualTo(503);
        verifyNoInteractions(preferences);
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
