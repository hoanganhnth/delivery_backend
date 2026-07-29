package com.delivery.match_service.listener;

import com.delivery.match_service.repository.MatchRedisGeoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

class ShipperLocationEventListenerTest {

    @Test
    void locationAcknowledgesOnlyAfterRedisMutation() {
        MatchRedisGeoRepository repository = mock(MatchRedisGeoRepository.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ShipperLocationEventListener listener = new ShipperLocationEventListener(repository);

        long timestamp = System.currentTimeMillis();
        listener.handleShipperLocationUpdated(
                "{\"shipperId\":7,\"latitude\":10.7,\"longitude\":106.6,"
                        + "\"isOnline\":true,\"timestamp\":" + timestamp + "}",
                acknowledgment);

        verify(repository).addOrUpdateShipperLocation(7L, 10.7, 106.6, true, timestamp);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void expiredOnlineReplayIsAcknowledgedWithoutResurrectingShipper() {
        MatchRedisGeoRepository repository = mock(MatchRedisGeoRepository.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ShipperLocationEventListener listener = new ShipperLocationEventListener(repository);

        listener.handleShipperLocationUpdated(
                "{\"shipperId\":7,\"latitude\":10.7,\"longitude\":106.6,"
                        + "\"isOnline\":true,\"timestamp\":123456789}",
                acknowledgment);

        verifyNoInteractions(repository);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void malformedLocationIsNotAcknowledged() {
        MatchRedisGeoRepository repository = mock(MatchRedisGeoRepository.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ShipperLocationEventListener listener = new ShipperLocationEventListener(repository);

        assertThrows(IllegalStateException.class,
                () -> listener.handleShipperLocationUpdated("{}", acknowledgment));

        verifyNoInteractions(repository, acknowledgment);
    }

    @Test
    void offlineTombstoneDoesNotRequireCoordinates() {
        MatchRedisGeoRepository repository = mock(MatchRedisGeoRepository.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ShipperLocationEventListener listener = new ShipperLocationEventListener(repository);

        listener.handleShipperLocationUpdated(
                "{\"shipperId\":7,\"latitude\":null,\"longitude\":null,"
                        + "\"isOnline\":false,\"timestamp\":123456790}",
                acknowledgment);

        verify(repository).markShipperOffline(7L, 123456790L);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void offlineRedisFailureIsNotAcknowledged() {
        MatchRedisGeoRepository repository = mock(MatchRedisGeoRepository.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ShipperLocationEventListener listener = new ShipperLocationEventListener(repository);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(repository).markShipperOffline(7L, 123456790L);

        assertThrows(IllegalStateException.class, () -> listener.handleShipperLocationUpdated(
                "{\"shipperId\":7,\"isOnline\":false,\"timestamp\":123456790}",
                acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void unsupportedStatusIsNotAcknowledged() {
        MatchRedisGeoRepository repository = mock(MatchRedisGeoRepository.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ShipperLocationEventListener listener = new ShipperLocationEventListener(repository);

        assertThrows(IllegalStateException.class, () -> listener.handleShipperStatusChange(
                "{\"shipperId\":7,\"status\":\"UNKNOWN\"}", acknowledgment));

        verifyNoInteractions(repository, acknowledgment);
    }

    @Test
    void redisStatusFailureIsNotAcknowledged() {
        MatchRedisGeoRepository repository = mock(MatchRedisGeoRepository.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ShipperLocationEventListener listener = new ShipperLocationEventListener(repository);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(repository).applyShipperStatus(
                        7L, 8L, "BUSY", 123456789L,
                        "11111111-1111-1111-1111-111111111111");

        assertThrows(IllegalStateException.class, () -> listener.handleShipperStatusChange(
                "{\"eventId\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"shipperId\":7,\"deliveryId\":8,\"orderId\":9,"
                        + "\"timestamp\":123456789,\"status\":\"BUSY\"}",
                acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void statusAcknowledgesOnlyAfterAtomicVersionedRedisMutation() {
        MatchRedisGeoRepository repository = mock(MatchRedisGeoRepository.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ShipperLocationEventListener listener = new ShipperLocationEventListener(repository);

        listener.handleShipperStatusChange(
                "{\"eventId\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"shipperId\":7,\"deliveryId\":8,\"orderId\":9,"
                        + "\"timestamp\":123456789,\"status\":\"available\"}",
                acknowledgment);

        verify(repository).applyShipperStatus(
                7L, 8L, "AVAILABLE", 123456789L,
                "11111111-1111-1111-1111-111111111111");
        verify(acknowledgment).acknowledge();
    }
}
