package com.delivery.notification_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.notification_service.exception.NotificationAccessDeniedException;
import com.delivery.notification_service.service.FirebaseService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;

class FirebaseControllerAuthorizationTest {

    private final FirebaseService firebaseService = mock(FirebaseService.class);
    private final FirebaseController controller = new FirebaseController(firebaseService);

    @Test
    void registerAndUnregisterRejectNonMobileRoles() {
        FirebaseController.TokenRequest request = validTokenRequest();
        AuthenticatedActor adminActor = new AuthenticatedActor(42L, "admin@example.com", Set.of("ADMIN"));
        AuthenticatedActor shopActor = new AuthenticatedActor(42L, "shop@example.com", Set.of("SHOP_OWNER"));

        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.registerFcmToken(adminActor, request));
        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.unregisterFcmToken(shopActor, request));

        verifyNoInteractions(firebaseService);
    }

    @Test
    void registerAndUnregisterAcceptCustomerAndShipperRoles() {
        FirebaseController.TokenRequest request = validTokenRequest();
        AuthenticatedActor userActor = new AuthenticatedActor(42L, "user@example.com", Set.of("USER"));
        AuthenticatedActor shipperActor = new AuthenticatedActor(84L, "shipper@example.com", Set.of("SHIPPER"));

        var customerRegister = controller.registerFcmToken(userActor, request);
        var customerUnregister = controller.unregisterFcmToken(userActor, request);
        var shipperRegister = controller.registerFcmToken(shipperActor, request);
        var shipperUnregister = controller.unregisterFcmToken(shipperActor, request);

        verify(firebaseService).registerFcmToken(42L, "device-token");
        verify(firebaseService).unregisterFcmToken(42L, "device-token");
        verify(firebaseService).registerFcmToken(84L, "device-token");
        verify(firebaseService).unregisterFcmToken(84L, "device-token");
        assertThat(customerRegister.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(customerUnregister.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(shipperRegister.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(shipperUnregister.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private FirebaseController.TokenRequest validTokenRequest() {
        FirebaseController.TokenRequest request = new FirebaseController.TokenRequest();
        request.setToken("device-token");
        return request;
    }
}
