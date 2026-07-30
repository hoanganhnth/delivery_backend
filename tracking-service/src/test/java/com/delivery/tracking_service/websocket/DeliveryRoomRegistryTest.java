package com.delivery.tracking_service.websocket;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class DeliveryRoomRegistryTest {

    @Test
    void newerDeliveryEvictsOldAudienceForReusedShipper() {
        DeliveryRoomRegistry rooms = new DeliveryRoomRegistry();
        rooms.subscribe(100L, 42L, "old-customer");

        rooms.activate(200L, 42L);

        assertThat(rooms.subscribersForShipper(42L)).isEmpty();
        rooms.subscribe(200L, 42L, "new-customer");
        assertThat(rooms.subscribersForShipper(42L)).containsExactly("new-customer");
        assertThat(rooms.activeDelivery(42L)).isEqualTo(200L);
    }

    @Test
    void fanoutLookupAndDisconnectStayBoundedWithTenThousandUnrelatedRooms() {
        DeliveryRoomRegistry rooms = new DeliveryRoomRegistry();
        for (int i = 1; i <= 10_000; i++) {
            rooms.subscribe(i, 100_000L + i, "session-" + i);
        }
        rooms.subscribe(50_000L, 42L, "target-1");
        rooms.subscribe(50_000L, 42L, "target-2");

        assertTimeout(Duration.ofSeconds(1), () -> {
            assertThat(rooms.subscribersForShipper(42L))
                    .containsExactlyInAnyOrder("target-1", "target-2");
            rooms.removeSession("target-1");
        });
        assertThat(rooms.subscribersForShipper(42L)).containsExactly("target-2");
    }

    @Test
    void availableStatusClosesOnlyMatchingGenerationRoom() {
        DeliveryRoomRegistry rooms = new DeliveryRoomRegistry();
        rooms.subscribe(100L, 42L, "participant");
        rooms.activate(200L, 42L);

        rooms.end(100L, 42L);
        assertThat(rooms.activeDelivery(42L)).isEqualTo(200L);

        rooms.end(200L, 42L);
        assertThat(rooms.activeDelivery(42L)).isNull();
        assertThat(rooms.subscribersForShipper(42L)).isEmpty();
    }
}
