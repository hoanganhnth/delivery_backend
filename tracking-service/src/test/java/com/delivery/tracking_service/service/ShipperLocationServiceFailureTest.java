package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShipperLocationServiceFailureTest {

    private final ShipperLocationRepository repository = mock(ShipperLocationRepository.class);
    private final ShipperLocationWebSocketHandler webSocketHandler = mock(ShipperLocationWebSocketHandler.class);
    private final ShipperLocationEventPublisher publisher = mock(ShipperLocationEventPublisher.class);
    private final ShipperAvailabilityService availabilityService =
            new ShipperAvailabilityService(repository, publisher);
    private final ShipperLocationService service =
            new ShipperLocationService(repository, webSocketHandler, publisher, availabilityService);

    @Test
    void locationUpdateDoesNotReportSuccessWhenCanonicalRedisWriteFails() {
        UpdateLocationRequest request = new UpdateLocationRequest();
        request.setLatitude(10.77);
        request.setLongitude(106.70);
        request.setIsOnline(true);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(repository).cacheShipperLocation(eq(7L), any(ShipperLocationResponse.class));

        assertThatThrownBy(() -> service.updateLocation(7L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không thể cập nhật vị trí shipper");
    }

    @Test
    void locationUpdateRejectsNonFiniteTelemetryBeforePersistence() {
        UpdateLocationRequest request = new UpdateLocationRequest();
        request.setLatitude(10.77);
        request.setLongitude(106.70);
        request.setSpeed(Double.POSITIVE_INFINITY);
        request.setIsOnline(true);

        assertThatThrownBy(() -> service.updateLocation(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("speed is invalid");

        verifyNoInteractions(repository, webSocketHandler, publisher);
    }

    @Test
    void locationUpdateRejectsNullOnlineFlagBeforePersistence() {
        UpdateLocationRequest request = new UpdateLocationRequest();
        request.setLatitude(10.77);
        request.setLongitude(106.70);
        request.setIsOnline(null);

        assertThatThrownBy(() -> service.updateLocation(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("isOnline must be boolean");

        verifyNoInteractions(repository, webSocketHandler, publisher);
    }

    @Test
    void explicitOfflineUpdatesRedisAndPublishesMatchTombstone() {
        ShipperLocationResponse current = new ShipperLocationResponse();
        current.setShipperId(7L);
        current.setLatitude(10.77);
        current.setLongitude(106.70);
        current.setIsOnline(true);
        when(repository.getCachedShipperLocation(7L)).thenReturn(current);

        service.markShipperOffline(7L);

        verify(repository).cacheShipperLocation(eq(7L), eq(current));
        verify(repository, never()).removeShipperLocationCache(7L);
        verify(publisher).publishLocationUpdate(7L, 10.77, 106.70, false);
        verify(webSocketHandler).broadcastShipperLocation(current);
        org.assertj.core.api.Assertions.assertThat(current.getIsOnline()).isFalse();
        org.assertj.core.api.Assertions.assertThat(current.getUpdatedAt()).isNotBlank();
    }

    @Test
    void explicitOfflineWithoutCachedCoordinatesStillPublishesIdentityTombstone() {
        when(repository.getCachedShipperLocation(7L)).thenReturn(null);

        service.markShipperOffline(7L);

        verify(repository).removeShipperLocationCache(7L);
        verify(repository, never()).cacheShipperLocation(eq(7L), any());
        verify(publisher).publishLocationUpdate(7L, null, null, false);
        verify(webSocketHandler).broadcastShipperLocation(any(ShipperLocationResponse.class));
    }

    @Test
    void explicitOfflineWithIncompleteCachedLocationStillClearsTrackingMembership() {
        ShipperLocationResponse current = new ShipperLocationResponse();
        current.setShipperId(7L);
        current.setLatitude(10.77);
        current.setIsOnline(true);
        when(repository.getCachedShipperLocation(7L)).thenReturn(current);

        service.markShipperOffline(7L);

        verify(repository).cacheShipperLocation(7L, current);
        verify(publisher).publishLocationUpdate(7L, 10.77, null, false);
        org.assertj.core.api.Assertions.assertThat(current.getIsOnline()).isFalse();
    }

    @Test
    void explicitOfflineFailsClosedWhenRedisReadFails() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(repository).getCachedShipperLocation(7L);

        assertThatThrownBy(() -> service.markShipperOffline(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");

        verifyNoInteractions(publisher, webSocketHandler);
    }

    @Test
    void explicitOfflineReportsBrokerFailureAfterSafeRedisMutation() {
        when(repository.getCachedShipperLocation(7L)).thenReturn(null);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publishLocationUpdate(7L, null, null, false);

        assertThatThrownBy(() -> service.markShipperOffline(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker unavailable");

        verify(repository).removeShipperLocationCache(7L);
        verifyNoInteractions(webSocketHandler);
    }
}
