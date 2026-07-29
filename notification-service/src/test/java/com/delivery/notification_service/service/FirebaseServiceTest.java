package com.delivery.notification_service.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;

class FirebaseServiceTest {

    @Test
    void skipsPushWhenFirebaseIsNotConfigured() {
        FirebaseService service = new FirebaseService(Optional.empty(), null);

        assertThatCode(() -> service.sendPushNotificationToUser(1L, "title", "body", Map.of()))
                .doesNotThrowAnyException();
    }
}
