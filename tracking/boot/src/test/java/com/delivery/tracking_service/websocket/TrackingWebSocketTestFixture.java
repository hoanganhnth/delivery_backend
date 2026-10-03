package com.delivery.tracking_service.websocket;

import com.delivery.tracking.application.api.PublisherSessionUseCase;
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
            DeliveryTrackingAccessClient access, PublisherSessionUseCase publishers) {
        if (org.mockito.Mockito.mockingDetails(locations).isMock()) {
            org.mockito.Mockito.lenient().when(locations.cacheIfCurrentPublisher(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any())).thenReturn(true);
        }
        var handler = new AtomicReference<ShipperLocationWebSocketHandler>();
        var rooms = new DeliveryRoomRegistry();
        var reads = new com.delivery.tracking.application.api.FanoutDeliveryReadPort() {
            public java.util.Set<Long> activeDeliveries(Long shipperId) { return rooms.activeDeliveries(shipperId); }
            public Optional<Long> activeDelivery(Long shipperId) { return Optional.ofNullable(rooms.activeDelivery(shipperId)); }
        };
        var fanoutEvents = new com.delivery.tracking.application.api.LocationFanoutEventPort() {
            public void publish(Long deliveryId, com.delivery.tracking.application.api.FanoutLocation location) {
                handler.get().broadcastDeliveryLocation(deliveryId, FanoutLocationMapper.toResponse(location));
            }
            public void failed(Long shipperId, Exception failure) { throw new AssertionError(failure); }
        };
        var fanout = new LocationFanoutPublisher(new com.delivery.tracking.application.DefaultLocationFanoutUseCase(reads, fanoutEvents));
        var adapter = new RedisLocationUpdateAdapter(locations, events, fanout);
        var identities = new ShipperIdentityResolver(new DefaultShipperIdentityUseCase(id -> Optional.empty()), false, new SimpleMeterRegistry());
        var subscriptions = new com.delivery.tracking.application.DefaultDeliveryRoomSubscriptionUseCase(
                org.mockito.Mockito.mock(com.delivery.tracking.application.api.DeliveryRoomAssignmentPort.class), rooms);
        var result = new ShipperLocationWebSocketHandler(new ObjectMapper(), locations, new DefaultTrackingService(adapter, adapter),
                access, publishers, rooms, new LocationMessageDispatcher(Runnable::run), identities, subscriptions);
        handler.set(result); return result;
    }
}
