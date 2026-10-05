package com.delivery.notification.application;

import com.delivery.notification.application.api.*;
import com.delivery.notification.domain.EventIdentity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IncomingEventsTest {
    static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final Offer OFFER = new Offer(EVENT,1L,2L," R "," P "," D ",List.of(new SelectedShipper(5L,1.25)));
    static Status status(String name) { return new Status(EVENT,1L,2L,7L,71L,"ASSIGNED",name); }
    static class OfferFixture implements OfferEventPort {
        List<String> calls = new ArrayList<>(); boolean simulation; RuntimeException contextFailure, sendFailure;
        public void validateSimulationContext() { calls.add("context"); if(contextFailure != null) throw contextFailure; }
        public boolean isSimulation() { calls.add("simulation"); return simulation; }
        public void send(Offer offer, SelectedShipper selected) {
            calls.add("send"); assertSame(OFFER,offer); assertEquals(new SelectedShipper(5L,1.25),selected);
            if(sendFailure != null) throw sendFailure;
        }
    }
    static class StatusFixture implements StatusEventPort {
        List<String> calls = new ArrayList<>(); Status sent; RuntimeException contextFailure, sendFailure;
        public void validateSimulationContext() { calls.add("context"); if(contextFailure != null) throw contextFailure; }
        public void send(Status event) { calls.add("send"); sent = event; if(sendFailure != null) throw sendFailure; }
    }
    @Test void offerValidatesBeforeContextAndOnlySimulationSkipsDispatch() {
        var f = new OfferFixture(); var incoming = new IncomingOffer(f);
        assertThrows(IllegalArgumentException.class,() -> incoming.handle(null)); assertTrue(f.calls.isEmpty());
        f.simulation = true; assertFalse(incoming.handle(OFFER)); assertEquals(List.of("context","simulation"),f.calls);
        f.calls.clear(); f.simulation = false; assertTrue(incoming.handle(OFFER)); assertEquals(List.of("context","simulation","send"),f.calls);
    }
    @Test void offerContextAndDispatchFailuresStopAtOriginalBoundary() {
        var f = new OfferFixture(); f.contextFailure = new IllegalArgumentException("invalid simulation");
        assertSame(f.contextFailure,assertThrows(IllegalArgumentException.class,() -> new IncomingOffer(f).handle(OFFER)));
        assertEquals(List.of("context"),f.calls);
        f.contextFailure = null; f.calls.clear(); f.sendFailure = new IllegalStateException("provider");
        assertSame(f.sendFailure,assertThrows(IllegalStateException.class,() -> new IncomingOffer(f).handle(OFFER)));
        assertEquals(List.of("context","simulation","send"),f.calls);
    }
    @Test void statusValidatesBeforeContextNormalizesOnlyBlankNameAndPreservesReplayIdentity() {
        var f = new StatusFixture(); var incoming = new IncomingStatus(f);
        assertThrows(IllegalArgumentException.class,() -> incoming.handle(new Status(null,null,null,null,null,null,null)));
        assertTrue(f.calls.isEmpty());
        for(String name : new String[]{null,""," \t"," A "}) {
            f.calls.clear(); incoming.handle(status(name));
            assertEquals(List.of("context","send"),f.calls);
            assertEquals(name == null || name.isBlank() ? null : name,f.sent.shipperName());
            assertEquals(EVENT,f.sent.eventId()); assertEquals(7L,f.sent.userId()); assertEquals(71L,f.sent.principalId());
            assertEquals(1L,f.sent.deliveryId()); assertEquals(2L,f.sent.orderId());
        }
        incoming.handle(status(" A ")); assertEquals(status(" A "),f.sent);
    }
    @Test void statusStillDispatchesReturnStatusesAfterContextValidationAndPropagatesFailures() {
        var f = new StatusFixture(); var incoming = new IncomingStatus(f);
        for(String state : new String[]{"RETURNING","RETURNED"}) {
            var status = new Status(EVENT,1L,2L,7L,null,state,null); incoming.handle(status); assertEquals(status,f.sent);
        }
        f.calls.clear(); f.contextFailure = new IllegalArgumentException("simulation");
        assertSame(f.contextFailure,assertThrows(IllegalArgumentException.class,() -> incoming.handle(status(null))));
        assertEquals(List.of("context"),f.calls);
        f.contextFailure = null; f.sendFailure = new IllegalStateException("send");
        assertSame(f.sendFailure,assertThrows(IllegalStateException.class,() -> incoming.handle(status(null))));
    }
}
