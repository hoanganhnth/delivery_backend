package com.delivery.tracking_service.websocket;

import com.delivery.tracking.application.DefaultTrackingService;
import com.delivery.tracking.application.DefaultShipperIdentityUseCase;
import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Real application cores; only external Redis/Kafka/fanout collaborators are test fixtures. */
final class TrackingWebSocketTestFixture {
    static ShipperLocationWebSocketHandler create(RedisGeoRepository locations, ShipperLocationEventPublisher events,
            DeliveryTrackingAccessClient access, ShipperPublisherSessionManager publishers) {
        var handler = new AtomicReference<ShipperLocationWebSocketHandler>();
        var fanout = new LocationFanoutPublisher(location -> {}) {
            @Override public void publish(com.delivery.tracking_service.dto.response.ShipperLocationResponse location) {
                handler.get().broadcastShipperLocation(location);
            }
        };
        var adapter = new RedisLocationUpdateAdapter(locations, events, fanout);
        var identities = new ShipperIdentityResolver(new DefaultShipperIdentityUseCase(id -> Optional.empty()), false, new SimpleMeterRegistry());
        var rooms = new DeliveryRoomRegistry();
        var subscriptions = new com.delivery.tracking.application.DefaultDeliveryRoomSubscriptionUseCase(
                org.mockito.Mockito.mock(com.delivery.tracking.application.api.DeliveryRoomAssignmentPort.class), rooms);
        var result = new ShipperLocationWebSocketHandler(new ObjectMapper(), locations, new DefaultTrackingService(adapter, adapter),
                access, publishers, rooms, new LocationMessageDispatcher(Runnable::run), fanout, identities, subscriptions);
        handler.set(result); return result;
    }
}
