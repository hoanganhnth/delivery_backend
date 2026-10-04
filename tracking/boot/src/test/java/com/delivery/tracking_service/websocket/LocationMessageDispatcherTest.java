package com.delivery.tracking_service.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationMessageDispatcherTest {

    @Test
    void slowSubscriberQueueCoalescesLocationsButRetainsOfflineAndLatestOnline() throws Exception {
        ControlledExecutor executor = new ControlledExecutor();
        LocationMessageDispatcher dispatcher = new LocationMessageDispatcher(executor);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("slow");
        when(session.isOpen()).thenReturn(true, true, true, false);

        for (int i = 0; i < 100; i++) {
            dispatcher.dispatch(session, 10L, new TextMessage("online-" + i), true, () -> true);
        }
        dispatcher.dispatch(session, 10L, new TextMessage("offline"), false, () -> true);
        dispatcher.dispatch(session, 10L, new TextMessage("online-latest"), true, () -> true);

        assertThat(executor.tasks).hasSize(1);
        executor.tasks.get(0).run();

        verify(session).sendMessage(new TextMessage("offline"));
        verify(session).sendMessage(new TextMessage("online-latest"));
        assertThat(dispatcher.stats().offered()).isEqualTo(102);
        assertThat(dispatcher.stats().sent()).isEqualTo(2);
        assertThat(dispatcher.stats().coalesced()).isEqualTo(100);
    }

    @Test
    void queuedMessageFromPriorMembershipIsDroppedAfterUnsubscribeAndRejoin() throws Exception {
        var executor=new ControlledExecutor(); var dispatcher=new LocationMessageDispatcher(executor);
        var rooms=new DeliveryRoomRegistry(); rooms.subscribe(100,42,"session");
        var session=mock(WebSocketSession.class); when(session.getId()).thenReturn("session"); when(session.isOpen()).thenReturn(true);
        Long prior=rooms.membershipVersion(100,42,"session");
        dispatcher.dispatch(session,100,new TextMessage("old"),true,()->prior.equals(rooms.membershipVersion(100,42,"session")));
        rooms.unsubscribe("session",42); rooms.subscribe(100,42,"session");
        Long current=rooms.membershipVersion(100,42,"session"); assertThat(current).isNotEqualTo(prior);
        dispatcher.dispatch(session,100,new TextMessage("new"),false,()->current.equals(rooms.membershipVersion(100,42,"session")));
        executor.tasks.get(0).run();
        org.mockito.Mockito.verify(session,org.mockito.Mockito.never()).sendMessage(new TextMessage("old"));
        verify(session).sendMessage(new TextMessage("new")); assertThat(dispatcher.stats().sent()).isEqualTo(1);
    }
    @Test
    void endingOneBatchRoomRevokesItsQueuedMessageWithoutDroppingSibling() throws Exception {
        var executor=new ControlledExecutor(); var dispatcher=new LocationMessageDispatcher(executor);
        var rooms=new DeliveryRoomRegistry(); rooms.synchronize(42,java.util.Set.of(100L,101L));
        rooms.subscribe(100,42,"session"); rooms.subscribe(101,42,"session");
        var session=mock(WebSocketSession.class); when(session.getId()).thenReturn("session"); when(session.isOpen()).thenReturn(true);
        for(long delivery:java.util.List.of(100L,101L)) {
            Long version=rooms.membershipVersion(delivery,42,"session");
            dispatcher.dispatch(session,delivery,new TextMessage("room-"+delivery),true,()->version.equals(rooms.membershipVersion(delivery,42,"session")));
        }
        rooms.end(100,42); executor.tasks.get(0).run();
        org.mockito.Mockito.verify(session,org.mockito.Mockito.never()).sendMessage(new TextMessage("room-100"));
        verify(session).sendMessage(new TextMessage("room-101")); assertThat(dispatcher.stats().sent()).isEqualTo(1);
    }
    @Test
    void reassignmentRevokesOldOwnerQueuedLocation() throws Exception {
        var executor=new ControlledExecutor(); var dispatcher=new LocationMessageDispatcher(executor);
        var rooms=new DeliveryRoomRegistry(); rooms.subscribe(100,42,"old");
        var session=mock(WebSocketSession.class); when(session.getId()).thenReturn("old"); when(session.isOpen()).thenReturn(true);
        Long version=rooms.membershipVersion(100,42,"old");
        dispatcher.dispatch(session,100,new TextMessage("old-owner"),true,()->version.equals(rooms.membershipVersion(100,42,"old")));
        rooms.subscribe(100,43,"new"); executor.tasks.get(0).run();
        org.mockito.Mockito.verify(session,org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.any());
        assertThat(dispatcher.stats().sent()).isZero();
    }

    private static final class ControlledExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }
}
