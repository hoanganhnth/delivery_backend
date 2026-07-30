package com.delivery.tracking_service.websocket;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.service.ShipperLocationEventPublisher;
import com.delivery.tracking_service.service.DeliveryTrackingAccessClient;
import com.delivery.tracking_service.service.ShipperPublisherSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipperLocationWebSocketHandlerAuthorizationTest {

    @Mock RedisGeoRepository repository;
    @Mock ShipperLocationEventPublisher publisher;
    @Mock DeliveryTrackingAccessClient trackingAccessClient;
    @Mock ShipperPublisherSessionManager publisherSessionManager;
    @Mock WebSocketSession session;

    private ShipperLocationWebSocketHandler handler;
    private HashMap<String, Object> attributes;

    @BeforeEach
    void setUp() {
        handler = new ShipperLocationWebSocketHandler(
                new ObjectMapper(), repository, publisher, trackingAccessClient,
                publisherSessionManager);
        attributes = new HashMap<>();
        lenient().when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("session-1");
    }

    @Test
    void locationUpdateUsesAuthenticatedIdentityInsteadOfPayloadShipperId() throws Exception {
        attributes.put("authenticatedUserId", 42L);
        attributes.put("authenticatedRole", "SHIPPER");
        establishPublisher();

        handler.handleTextMessage(session, new TextMessage("""
                {"action":"update_location","shipperId":999,"latitude":10.75,"longitude":106.67}
                """));

        ArgumentCaptor<ShipperLocationResponse> location = ArgumentCaptor.forClass(ShipperLocationResponse.class);
        verify(repository).cacheShipperLocation(eq(42L), location.capture());
        verify(publisher).publishLocationUpdate(any(ShipperLocationResponse.class), eq("WEBSOCKET"));
        assertThat(location.getValue().getShipperId()).isEqualTo(42L);
        assertThat(location.getValue().getAccuracy()).isNull();
        assertThat(location.getValue().getSpeed()).isNull();
        assertThat(location.getValue().getHeading()).isNull();
        assertThat(location.getValue().getUpdatedAt()).isNotBlank();
    }

    @Test
    void broadcastPreservesUnknownOptionalMotionFieldsAsNull() throws Exception {
        attributes.put("authenticatedUserId", 7L);
        attributes.put("authenticatedRole", "USER");
        when(trackingAccessClient.canTrack(100L, 7L, "USER", 42L)).thenReturn(true);
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"subscribe_shipper\",\"deliveryId\":100,\"shipperId\":42}"));

        ShipperLocationResponse location = new ShipperLocationResponse();
        location.setShipperId(42L);
        location.setLatitude(10.75);
        location.setLongitude(106.67);
        location.setIsOnline(true);
        location.setUpdatedAt("2026-07-28T20:00:00");

        handler.broadcastShipperLocation(location);

        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("\"accuracy\":null")
                        && message.getPayload().contains("\"speed\":null")
                        && message.getPayload().contains("\"heading\":null")
                        && message.getPayload().contains("\"timestamp\":\"2026-07-28T20:00:00\"")));
    }

    @Test
    void nonShipperCannotPublishLocation() throws Exception {
        attributes.put("authenticatedUserId", 7L);
        attributes.put("authenticatedRole", "USER");

        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"update_location\",\"latitude\":10.75,\"longitude\":106.67}"));

        verifyNoInteractions(repository, publisher);
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void brokerFailureIsReportedToShipperInsteadOfSilentlyDroppingReplicaUpdate() throws Exception {
        attributes.put("authenticatedUserId", 42L);
        attributes.put("authenticatedRole", "SHIPPER");
        when(session.isOpen()).thenReturn(true);
        establishPublisher();
        doThrow(new IllegalStateException("Cannot replicate shipper location"))
                .when(publisher).publishLocationUpdate(any(ShipperLocationResponse.class), eq("WEBSOCKET"));

        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"update_location\",\"latitude\":10.75,\"longitude\":106.67}"));

        verify(repository).cacheShipperLocation(eq(42L), any(ShipperLocationResponse.class));
        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("MESSAGE_PROCESSING_FAILED")));
    }

    @Test
    void nonFiniteOptionalTelemetryIsRejectedBeforePublishing() throws Exception {
        attributes.put("authenticatedUserId", 42L);
        attributes.put("authenticatedRole", "SHIPPER");
        when(session.isOpen()).thenReturn(true);
        establishPublisher();

        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"update_location\",\"latitude\":10.75,\"longitude\":106.67,\"speed\":1e309}"));

        verifyNoInteractions(repository, publisher);
        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("MESSAGE_PROCESSING_FAILED")));
    }

    @Test
    void invalidOnlineFlagIsRejectedBeforePublishing() throws Exception {
        attributes.put("authenticatedUserId", 42L);
        attributes.put("authenticatedRole", "SHIPPER");
        when(session.isOpen()).thenReturn(true);
        establishPublisher();

        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"update_location\",\"latitude\":10.75,\"longitude\":106.67,\"isOnline\":null}"));

        verifyNoInteractions(repository, publisher);
        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("MESSAGE_PROCESSING_FAILED")));
    }

    @Test
    void participantCanSubscribeToAssignedShipperForActiveDelivery() throws Exception {
        attributes.put("authenticatedUserId", 7L);
        attributes.put("authenticatedRole", "USER");
        when(trackingAccessClient.canTrack(100L, 7L, "USER", 42L)).thenReturn(true);

        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"subscribe_shipper\",\"deliveryId\":100,\"shipperId\":42}"));

        verify(trackingAccessClient).canTrack(100L, 7L, "USER", 42L);
        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("subscription_confirmed")));
    }

    @Test
    void subscriberRecoversLatestRedisLocationAfterReconnect() throws Exception {
        attributes.put("authenticatedUserId", 7L);
        attributes.put("authenticatedRole", "USER");
        when(session.isOpen()).thenReturn(true);
        when(trackingAccessClient.canTrack(100L, 7L, "USER", 42L)).thenReturn(true);
        ShipperLocationResponse latest = new ShipperLocationResponse();
        latest.setShipperId(42L);
        latest.setLatitude(10.77);
        latest.setLongitude(106.70);
        latest.setIsOnline(true);
        latest.setUpdatedAt("2026-07-30T04:00:00");
        when(repository.getCachedShipperLocation(42L)).thenReturn(latest);
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"subscribe_shipper\",\"deliveryId\":100,\"shipperId\":42}"));

        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("\"type\":\"location_update\"")
                        && message.getPayload().contains("\"latitude\":10.77")));
    }

    @Test
    void nonParticipantCannotSubscribe() throws Exception {
        attributes.put("authenticatedUserId", 9L);
        attributes.put("authenticatedRole", "USER");
        when(trackingAccessClient.canTrack(100L, 9L, "USER", 42L)).thenReturn(false);

        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"subscribe_shipper\",\"deliveryId\":100,\"shipperId\":42}"));

        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("FORBIDDEN")));
    }

    @Test
    void arbitraryAreaTrackingIsRejectedEvenForAdmin() throws Exception {
        handler.handleTextMessage(session, new TextMessage(
                "{\"action\":\"subscribe_area\",\"latitude\":10.75,\"longitude\":106.67,\"radius\":5}"));

        verifyNoInteractions(repository, publisher, trackingAccessClient);
        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("Area tracking is not available in MVP")));
    }

    private void establishPublisher() throws Exception {
        PublisherLease lease = new PublisherLease(42L, "session-1", 1L);
        when(publisherSessionManager.acquire(42L, "session-1")).thenReturn(lease);
        when(publisherSessionManager.refreshIfCurrent(lease)).thenReturn(true);
        handler.afterConnectionEstablished(session);
    }
}
