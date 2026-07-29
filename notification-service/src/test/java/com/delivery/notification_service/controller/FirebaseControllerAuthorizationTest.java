package com.delivery.notification_service.controller;

import com.delivery.notification_service.exception.NotificationAccessDeniedException;
import com.delivery.notification_service.service.FirebaseService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;

class FirebaseControllerAuthorizationTest {

    private final FirebaseService firebaseService = mock(FirebaseService.class);
    private final FirebaseController controller = new FirebaseController(firebaseService);

    @Test
    void registerAndUnregisterRequireUserRole() {
        FirebaseController.TokenRequest request = validTokenRequest();

        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.registerFcmToken(42L, "ADMIN", request));
        assertThrows(NotificationAccessDeniedException.class,
                () -> controller.unregisterFcmToken(42L, "SHOP_OWNER", request));

        verifyNoInteractions(firebaseService);
    }

    @Test
    void registerAndUnregisterAcceptUserRole() {
        FirebaseController.TokenRequest request = validTokenRequest();

        var registerResponse = controller.registerFcmToken(42L, "USER", request);
        var unregisterResponse = controller.unregisterFcmToken(42L, "USER", request);

        verify(firebaseService).registerFcmToken(42L, "device-token");
        verify(firebaseService).unregisterFcmToken(42L, "device-token");
        assertThat(registerResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(unregisterResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private FirebaseController.TokenRequest validTokenRequest() {
        FirebaseController.TokenRequest request = new FirebaseController.TokenRequest();
        request.setToken("device-token");
        return request;
    }
}
