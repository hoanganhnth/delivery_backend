package com.delivery.notification.application;

import com.delivery.notification.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventNotificationsTest {
    static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Test void eventUseCasesPreserveRoutingIdentityAndSendOnce() {
        List<NotificationIntent> sent = new ArrayList<>(); var events = new EventNotifications(sent::add);
        events.orderCreated(EVENT,7L,71L,9L," R ");
        events.deliveryStatus(EVENT,7L,71L,11L,"ASSIGNED"," A ");
        events.shipperOffer(5L,9L,"R","P","D",1.25,EVENT.toString());
        assertEquals(3,sent.size());
        var order = sent.get(0); assertEquals("order-created:"+EVENT,order.deduplicationKey());
        assertEquals(7L,order.userId()); assertEquals(71L,order.userPrincipalId()); assertEquals(9L,order.relatedEntityId());
        assertEquals("Đơn hàng #9 từ  R  đã được tạo thành công",order.message());
        var status = sent.get(1); assertEquals("delivery-status:"+EVENT,status.deduplicationKey());
        assertEquals(7L,status.userId()); assertEquals(71L,status.userPrincipalId()); assertEquals(11L,status.relatedEntityId());
        assertEquals(" A  đã được phân công giao đơn hàng của bạn",status.message());
        var offer = sent.get(2); assertEquals("shipper-offer:"+EVENT+":5",offer.deduplicationKey());
        assertEquals(5L,offer.userId()); assertNull(offer.userPrincipalId()); assertTrue(offer.sendPush());
        assertEquals("/api/deliveries/offers/current",offer.data().get("recoveryEndpoint"));
    }
    @Test void invalidMappingsNeverDispatchAndDirectOfferRemainsPermissive() {
        List<NotificationIntent> sent = new ArrayList<>(); var events = new EventNotifications(sent::add);
        assertThrows(IllegalArgumentException.class,() -> events.orderCreated(null,null,null,null,null));
        for(String status : new String[]{"RETURNING","RETURNED"}) assertThrows(IllegalArgumentException.class,
                () -> events.deliveryStatus(EVENT,7L,null,11L,status,null));
        assertTrue(sent.isEmpty());
        events.shipperOffer(5L,9L,null,null,null,null,null);
        assertEquals("shipper-offer:null:5",sent.get(0).deduplicationKey()); assertTrue(sent.get(0).message().contains("null"));
    }
    @Test void localeStillChangesReplayMessageIdentityAndFailuresPropagate() {
        Locale previous = Locale.getDefault(); List<NotificationIntent> sent = new ArrayList<>(); var events = new EventNotifications(sent::add);
        try {
            Locale.setDefault(Locale.US); events.shipperOffer(5L,9L,"R","P","D",1.25,"event");
            Locale.setDefault(Locale.GERMANY); events.shipperOffer(5L,9L,"R","P","D",1.25,"event");
            assertEquals(sent.get(0).deduplicationKey(),sent.get(1).deduplicationKey());
            assertNotEquals(sent.get(0).message(),sent.get(1).message());
        } finally { Locale.setDefault(previous); }
        var failure = new IllegalStateException("send failed"); var failed = new EventNotifications(intent -> {throw failure;});
        assertSame(failure,assertThrows(IllegalStateException.class,() -> failed.orderCreated(EVENT,7L,null,9L,"R")));
        assertSame(failure,assertThrows(IllegalStateException.class,() -> failed.deliveryStatus(EVENT,7L,null,11L,"PENDING",null)));
        assertSame(failure,assertThrows(IllegalStateException.class,() -> failed.shipperOffer(5L,9L,"R","P","D",1.0,"event")));
    }
}
