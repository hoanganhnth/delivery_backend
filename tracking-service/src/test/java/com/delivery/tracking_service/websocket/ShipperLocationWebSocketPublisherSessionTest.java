package com.delivery.tracking_service.websocket;

import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.service.DeliveryTrackingAccessClient;
import com.delivery.tracking_service.service.ShipperLocationEventPublisher;
import com.delivery.tracking_service.service.ShipperPublisherSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShipperLocationWebSocketPublisherSessionTest {

    @Test
    void newerConnectionSupersedesOldPublisherAndOldCloseCannotOwnOffline() throws Exception {
        RedisGeoRepository locations = mock(RedisGeoRepository.class);
        ShipperLocationEventPublisher publisher = mock(ShipperLocationEventPublisher.class);
        ShipperPublisherSessionManager sessions = mock(ShipperPublisherSessionManager.class);
        ShipperLocationWebSocketHandler handler = new ShipperLocationWebSocketHandler(
                new ObjectMapper(), locations, publisher,
                mock(DeliveryTrackingAccessClient.class), sessions);
        WebSocketSession oldSession = shipperSession("old", 7L);
        WebSocketSession newSession = shipperSession("new", 7L);
        PublisherLease oldLease = new PublisherLease(7L, "old", 1L);
        PublisherLease newLease = new PublisherLease(7L, "new", 2L);
        when(sessions.acquire(7L, "old")).thenReturn(oldLease);
        when(sessions.acquire(7L, "new")).thenReturn(newLease);
        when(oldSession.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(oldSession);
        handler.afterConnectionEstablished(newSession);
        handler.afterConnectionClosed(oldSession, CloseStatus.NORMAL);

        verify(oldSession).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("PUBLISHER_SUPERSEDED")));
        verify(oldSession).close(any(CloseStatus.class));
        verify(sessions).disconnected(eq(oldLease), any());
        verify(sessions, never()).disconnected(eq(newLease), any());
    }

    @Test
    void fencedPublisherCannotWriteLocation() throws Exception {
        RedisGeoRepository locations = mock(RedisGeoRepository.class);
        ShipperLocationEventPublisher publisher = mock(ShipperLocationEventPublisher.class);
        ShipperPublisherSessionManager sessions = mock(ShipperPublisherSessionManager.class);
        ShipperLocationWebSocketHandler handler = new ShipperLocationWebSocketHandler(
                new ObjectMapper(), locations, publisher,
                mock(DeliveryTrackingAccessClient.class), sessions);
        WebSocketSession session = shipperSession("old", 7L);
        PublisherLease lease = new PublisherLease(7L, "old", 1L);
        when(sessions.acquire(7L, "old")).thenReturn(lease);
        when(sessions.refreshIfCurrent(lease)).thenReturn(false);

        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"update_location\",\"latitude\":10.75,\"longitude\":106.67}"));

        verifyNoInteractions(locations, publisher);
        verify(session).close(any(CloseStatus.class));
    }

    private WebSocketSession shipperSession(String id, Long shipperId) {
        WebSocketSession session = mock(WebSocketSession.class);
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("authenticatedUserId", shipperId);
        attributes.put("authenticatedRole", "SHIPPER");
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
