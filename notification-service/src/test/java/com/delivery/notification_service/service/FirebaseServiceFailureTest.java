package com.delivery.notification_service.service;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseServiceFailureTest {

    @Test
    void absentOptionalFirebaseKeepsInboxDeliveryAvailable() {
        RedisService redisService = mock(RedisService.class);
        FirebaseService service = new FirebaseService(Optional.empty(), redisService);

        assertThatCode(() -> service.sendPushNotificationToUser(
                7L, "Title", "Body", Map.of("type", "ORDER_CREATED")))
                .doesNotThrowAnyException();

        verify(redisService, never()).getUserFcmTokens(7L);
    }

    @Test
    void configuredFirebasePropagatesRedisFailureForDurableRetry() {
        RedisService redisService = mock(RedisService.class);
        FirebaseApp firebaseApp = mock(FirebaseApp.class);
        FirebaseService service = new FirebaseService(Optional.of(firebaseApp), redisService);
        when(redisService.getUserFcmTokens(7L))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatThrownBy(() -> service.sendPushNotificationToUser(
                7L, "Title", "Body", Map.of("type", "ORDER_CREATED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
    }

    @Test
    void configuredFirebaseWithoutTokensIsSuccessfulNoop() {
        RedisService redisService = mock(RedisService.class);
        FirebaseApp firebaseApp = mock(FirebaseApp.class);
        FirebaseService service = new FirebaseService(Optional.of(firebaseApp), redisService);
        when(redisService.getUserFcmTokens(7L)).thenReturn(Set.of());

        assertThatCode(() -> service.sendPushNotificationToUser(
                7L, "Title", "Body", Map.of("type", "ORDER_CREATED")))
                .doesNotThrowAnyException();

        verify(redisService).getUserFcmTokens(7L);
    }
}
