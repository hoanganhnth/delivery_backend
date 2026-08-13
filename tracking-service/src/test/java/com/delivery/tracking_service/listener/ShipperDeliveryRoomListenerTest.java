package com.delivery.tracking_service.listener;

import com.delivery.tracking_service.websocket.DeliveryRoomRegistry;
import com.delivery.tracking_service.repository.ShipperDeliveryAssignmentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ShipperDeliveryRoomListenerTest {

    private final DeliveryRoomRegistry rooms = new DeliveryRoomRegistry();
    private final ShipperDeliveryAssignmentStore assignments = mock(ShipperDeliveryAssignmentStore.class);
    private final ShipperDeliveryRoomListener listener =
            new ShipperDeliveryRoomListener(new ObjectMapper(), rooms, assignments);

    @Test
    void busyAndAvailableEventsFenceRoomByDelivery() {
        Acknowledgment busyAck = mock(Acknowledgment.class);
        listener.handle(event("BUSY", 100), busyAck);
        rooms.subscribe(100L, 42L, "participant");
        assertThat(rooms.subscribersForShipper(42L)).containsExactly("participant");

        Acknowledgment staleAck = mock(Acknowledgment.class);
        listener.handle(event("AVAILABLE", 99), staleAck);
        assertThat(rooms.activeDelivery(42L)).isEqualTo(100L);

        Acknowledgment availableAck = mock(Acknowledgment.class);
        listener.handle(event("AVAILABLE", 100), availableAck);
        assertThat(rooms.subscribersForShipper(42L)).isEmpty();
        verify(busyAck).acknowledge();
        verify(staleAck).acknowledge();
        verify(availableAck).acknowledge();
        verify(assignments).busy(42L, 100L, 1000L,
                "00000000-0000-0000-0000-000000000001");
        verify(assignments).available(42L, 99L, 1000L);
        verify(assignments).available(42L, 100L, 1000L);
    }

    @Test
    void malformedEventIsNotAcknowledged() {
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        assertThatThrownBy(() -> listener.handle("{\"shipperId\":42}", acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String event(String status, long deliveryId) {
        return "{\"eventId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"shipperId\":42,\"deliveryId\":" + deliveryId
                + ",\"orderId\":7,\"timestamp\":1000,\"status\":\"" + status + "\"}";
    }
}
