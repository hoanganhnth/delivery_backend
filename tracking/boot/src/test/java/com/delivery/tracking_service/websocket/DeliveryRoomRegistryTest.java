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
    @Test
    void batchAudienceSurvivesSiblingSubscribeAndOnlyFinishedItemIsClosed() {
        var rooms=new DeliveryRoomRegistry();
        rooms.synchronize(42,java.util.Set.of(100L,101L));
        rooms.subscribe(100,42,"first"); rooms.subscribe(101,42,"second");
        assertThat(rooms.subscribers(100,42)).containsExactly("first");
        assertThat(rooms.subscribers(101,42)).containsExactly("second");
        rooms.synchronize(42,java.util.Set.of(100L,101L));
        assertThat(rooms.subscribersForShipper(42)).containsExactlyInAnyOrder("first","second");
        rooms.end(100,42);
        assertThat(rooms.subscribers(100,42)).isEmpty();
        assertThat(rooms.subscribers(101,42)).containsExactly("second");
        assertThat(rooms.activeDeliveries(42)).containsExactly(101L);
    }
    @Test
    void batchUnsubscribeAndDisconnectCleanEveryRoomForTheSession() {
        var rooms=new DeliveryRoomRegistry(); rooms.synchronize(42,java.util.Set.of(100L,101L));
        rooms.subscribe(100,42,"shared"); rooms.subscribe(101,42,"shared");
        assertThat(rooms.subscribersForShipper(42)).containsExactly("shared");
        rooms.unsubscribe("shared",42); assertThat(rooms.subscribersForShipper(42)).isEmpty();
        rooms.subscribe(100,42,"shared"); rooms.subscribe(101,42,"shared");
        rooms.removeSession("shared"); assertThat(rooms.subscribersForShipper(42)).isEmpty();
        rooms.synchronize(42,java.util.Set.of()); assertThat(rooms.roomCount()).isZero();
    }

    @Test
    void authorizedReassignmentReplacesOldMembershipAndLateOldOwnerEndCannotRemoveNewRoom() {
        var rooms=new DeliveryRoomRegistry(); rooms.subscribe(100,42,"old-shipper-session");
        org.assertj.core.api.Assertions.assertThatCode(()->rooms.subscribe(100,43,"new-shipper-session")).doesNotThrowAnyException();
        assertThat(rooms.subscribers(100,42)).isEmpty();
        assertThat(rooms.subscribers(100,43)).containsExactly("new-shipper-session");
        rooms.activate(100,42); // A late local old-owner assignment must not own the new membership.
        rooms.end(100,42);
        assertThat(rooms.subscribers(100,43)).containsExactly("new-shipper-session");
        rooms.removeSession("old-shipper-session");
        assertThat(rooms.subscribers(100,43)).containsExactly("new-shipper-session");
    }

    @Test
    void watermarkResetsOnRejoinAndStaleMembershipCannotAdvanceNewSubscription() {
        var rooms = new DeliveryRoomRegistry();
        rooms.subscribe(100, 42, "customer");
        long old = rooms.membershipVersion(100, 42, "customer");
        assertThat(rooms.admitLocation(100, 42, "customer", old, 2000)).isTrue();
        rooms.unsubscribe("customer", 42);
        rooms.subscribe(100, 42, "customer");
        long current = rooms.membershipVersion(100, 42, "customer");
        assertThat(current).isNotEqualTo(old);
        assertThat(rooms.admitLocation(100, 42, "customer", old, 9000)).isFalse();
        assertThat(rooms.admitLocation(100, 42, "customer", current, 1000)).isTrue();
        rooms.end(100, 42);
        assertThat(rooms.admitLocation(100, 42, "customer", current, 2000)).isFalse();
    }

}
