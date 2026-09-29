package com.delivery.notification_service.listener;

import com.delivery.notification_service.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DeliveryEventListenerReplayTest {
    @Test
    void replayCarriesTheSameStableEventIdToNotificationDeduplication() {
        NotificationService service = mock(NotificationService.class);
        Acknowledgment ack = mock(Acknowledgment.class);
        String payload = "{\"eventId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"deliveryId\":8,\"orderId\":7,\"userId\":42,\"status\":\"ASSIGNED\"}";
        var listener = new DeliveryEventListener(service);
        listener.handleDeliveryStatusUpdatedEvent(payload, "delivery.status-updated", 0, 1L, ack);
        listener.handleDeliveryStatusUpdatedEvent(payload, "delivery.status-updated", 0, 2L, ack);
        verify(service, times(2)).sendDeliveryStatusNotification(
                eq(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")), eq(42L), any(), eq(8L), eq("ASSIGNED"), any());
        verify(ack, times(2)).acknowledge();
    }
}
