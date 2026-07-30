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
            dispatcher.dispatch(session, 10L, new TextMessage("online-" + i), true);
        }
        dispatcher.dispatch(session, 10L, new TextMessage("offline"), false);
        dispatcher.dispatch(session, 10L, new TextMessage("online-latest"), true);

        assertThat(executor.tasks).hasSize(1);
        executor.tasks.get(0).run();

        verify(session).sendMessage(new TextMessage("offline"));
        verify(session).sendMessage(new TextMessage("online-latest"));
        assertThat(dispatcher.stats().offered()).isEqualTo(102);
        assertThat(dispatcher.stats().sent()).isEqualTo(2);
        assertThat(dispatcher.stats().coalesced()).isEqualTo(100);
    }

    private static final class ControlledExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }
}
