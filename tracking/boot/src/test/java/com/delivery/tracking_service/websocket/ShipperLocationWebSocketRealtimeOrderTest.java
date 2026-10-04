package com.delivery.tracking_service.websocket;

import com.delivery.tracking.application.api.PublisherSessionUseCase;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.repository.StoredShipperLocation;
import com.delivery.tracking_service.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipperLocationWebSocketRealtimeOrderTest {
    @Test void delayedOlderOfflineDoesNotRegressAnAlreadyDeliveredOnlineFact() throws Exception {
        var fixture = subscribe(null);
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 2000L);
        fixture.handler.broadcastDeliveryLocation(100L, location(false), 1000L);
        assertThat(fixture.messages).hasSize(1);
        assertThat(fixture.messages.get(0)).contains("\"isOnline\":true");
    }

    @Test void olderOnlineAndEqualTimeReplayCannotUndoNewerOffline() throws Exception {
        var fixture = subscribe(null);
        fixture.handler.broadcastDeliveryLocation(100L, location(false), 2000L);
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 1000L);
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 2000L);
        assertThat(fixture.messages).hasSize(1);
        assertThat(fixture.messages.get(0)).contains("\"isOnline\":false");
    }

    @Test void increasingTimesKeepBothOnlineStateTransitions() throws Exception {
        var fixture = subscribe(null);
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 1000L);
        fixture.handler.broadcastDeliveryLocation(100L, location(false), 2000L);
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 3000L);
        assertThat(fixture.messages).hasSize(3);
        assertThat(fixture.messages.get(1)).contains("\"isOnline\":false");
        assertThat(fixture.messages.get(2)).contains("\"isOnline\":true");
    }

    @Test void reconnectBootstrapRejectsDelayedPubsubAndAllowsNewFact() throws Exception {
        var fixture = subscribe(new StoredShipperLocation(location(false), 2000L));
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 1000L);
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 2000L);
        assertThat(fixture.messages).isEmpty();
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 3000L);
        assertThat(fixture.messages).hasSize(1);
    }

    @Test void coordinateFreeOfflineBootstrapStillRejectsDelayedOnline() throws Exception {
        var fixture = subscribe(new StoredShipperLocation(null, 2000L));
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 1000L);
        assertThat(fixture.messages).isEmpty();
        fixture.handler.broadcastDeliveryLocation(100L, location(true), 3000L);
        assertThat(fixture.messages).hasSize(1);
    }

    private Fixture subscribe(StoredShipperLocation latest) throws Exception {
        var cache = mock(RedisGeoRepository.class);
        when(cache.getCachedProjection(7L)).thenReturn(latest);
        var access = mock(DeliveryTrackingAccessClient.class);
        when(access.canTrack(100L, 300L, "USER", 7L)).thenReturn(true);
        var handler = TrackingWebSocketTestFixture.create(cache, mock(ShipperLocationEventPublisher.class), access,
                mock(PublisherSessionUseCase.class));
        var messages = new ArrayList<String>(); var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("customer"); when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("authenticatedUserId", 300L,
                "authenticatedPrincipalId", 1300L, "authenticatedRole", "USER")));
        doAnswer(call -> { messages.add(((TextMessage) call.getArgument(0)).getPayload()); return null; }).when(session).sendMessage(any());
        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"action\":\"subscribe_shipper\",\"deliveryId\":100,\"shipperId\":7}"));
        messages.clear();
        return new Fixture(handler, messages);
    }

    private ShipperLocationResponse location(boolean online) {
        var row = new ShipperLocationResponse(); row.setShipperId(7L); row.setIsOnline(online);
        row.setLatitude(10.7); row.setLongitude(106.7);
        // Public local-time strings are deliberately unrelated to absolute ordering metadata.
        row.setUpdatedAt("2026-10-03T00:00:00"); row.setLastPing("2026-10-03T00:00:00");
        return row;
    }

    private record Fixture(ShipperLocationWebSocketHandler handler, List<String> messages) {}
}
