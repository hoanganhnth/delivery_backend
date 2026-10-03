package com.delivery.tracking_service.service;

import com.delivery.tracking.application.DefaultShipperAvailabilityUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.ArgumentCaptor;
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

class TrackingAvailabilityAdapterTest {

    private final ShipperLocationRepository repository = mock(ShipperLocationRepository.class);
    private final ShipperLocationWebSocketHandler webSocketHandler = mock(ShipperLocationWebSocketHandler.class);
    private final ShipperLocationEventPublisher publisher = mock(ShipperLocationEventPublisher.class);
    private final ObjectProvider<ShipperLocationWebSocketHandler> sockets = socketProvider();
    private final RedisShipperAvailabilityAdapter adapter = new RedisShipperAvailabilityAdapter(repository, publisher, sockets);
    private final com.delivery.tracking.application.api.ShipperAvailabilityUseCase availability =
            new DefaultShipperAvailabilityUseCase(adapter, adapter);

    @SuppressWarnings("unchecked")
    private ObjectProvider<ShipperLocationWebSocketHandler> socketProvider() {
        var provider = (ObjectProvider<ShipperLocationWebSocketHandler>) mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(webSocketHandler); return provider;
    }
    @Test
    void explicitOfflineUpdatesRedisAndPublishesMatchTombstone() {
        ShipperLocationResponse current = new ShipperLocationResponse();
        current.setShipperId(7L);
        current.setLatitude(10.77);
        current.setLongitude(106.70);
        current.setDistance(1.2);
        current.setIsOnline(true);
        when(repository.getCachedShipperLocation(7L)).thenReturn(current);

        availability.markOfflineAndBroadcast(7L);

        var saved = ArgumentCaptor.forClass(ShipperLocationResponse.class);
        verify(repository).cacheShipperLocation(eq(7L), saved.capture());
        var offline = saved.getValue();
        org.assertj.core.api.Assertions.assertThat(offline.getLatitude()).isEqualTo(current.getLatitude());
        org.assertj.core.api.Assertions.assertThat(offline.getLongitude()).isEqualTo(current.getLongitude());
        org.assertj.core.api.Assertions.assertThat(offline.getDistance()).isEqualTo(current.getDistance());
        verify(repository, never()).removeShipperLocationCache(7L);
        var event = ArgumentCaptor.forClass(ShipperLocationResponse.class);
        verify(publisher).publishLocationUpdate(event.capture(), eq("OFFLINE_TOMBSTONE"));
        org.assertj.core.api.Assertions.assertThat(event.getValue()).usingRecursiveComparison().isEqualTo(offline);
        var fanout = ArgumentCaptor.forClass(ShipperLocationResponse.class);
        verify(webSocketHandler).broadcastShipperLocation(fanout.capture());
        org.assertj.core.api.Assertions.assertThat(fanout.getValue()).usingRecursiveComparison().isEqualTo(offline);
        org.assertj.core.api.Assertions.assertThat(offline.getIsOnline()).isFalse();
        org.assertj.core.api.Assertions.assertThat(offline.getUpdatedAt()).isNotBlank();
    }

    @Test
    void explicitOfflineWithoutCachedCoordinatesStillPublishesIdentityTombstone() {
        when(repository.getCachedShipperLocation(7L)).thenReturn(null);

        availability.markOfflineAndBroadcast(7L);

        verify(repository).removeShipperLocationCache(7L);
        verify(repository, never()).cacheShipperLocation(eq(7L), any());
        verify(publisher).publishLocationUpdate(any(ShipperLocationResponse.class), eq("OFFLINE_TOMBSTONE"));
        verify(webSocketHandler).broadcastShipperLocation(any(ShipperLocationResponse.class));
    }

    @Test
    void explicitOfflineWithIncompleteCachedLocationStillClearsTrackingMembership() {
        ShipperLocationResponse current = new ShipperLocationResponse();
        current.setShipperId(7L);
        current.setLatitude(10.77);
        current.setIsOnline(true);
        when(repository.getCachedShipperLocation(7L)).thenReturn(current);

        availability.markOfflineAndBroadcast(7L);

        var saved = ArgumentCaptor.forClass(ShipperLocationResponse.class);
        verify(repository).cacheShipperLocation(eq(7L), saved.capture());
        var offline = saved.getValue();
        org.assertj.core.api.Assertions.assertThat(offline.getLatitude()).isEqualTo(10.77);
        org.assertj.core.api.Assertions.assertThat(offline.getLongitude()).isNull();
        var event = ArgumentCaptor.forClass(ShipperLocationResponse.class);
        verify(publisher).publishLocationUpdate(event.capture(), eq("OFFLINE_TOMBSTONE"));
        org.assertj.core.api.Assertions.assertThat(event.getValue()).usingRecursiveComparison().isEqualTo(offline);
        org.assertj.core.api.Assertions.assertThat(offline.getIsOnline()).isFalse();
    }

    @Test
    void explicitOfflineFailsClosedWhenRedisReadFails() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(repository).getCachedShipperLocation(7L);

        assertThatThrownBy(() -> availability.markOfflineAndBroadcast(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");

        verifyNoInteractions(publisher, webSocketHandler);
    }

    @Test
    void explicitOfflineReportsBrokerFailureAfterSafeRedisMutation() {
        when(repository.getCachedShipperLocation(7L)).thenReturn(null);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publishLocationUpdate(any(ShipperLocationResponse.class), eq("OFFLINE_TOMBSTONE"));

        assertThatThrownBy(() -> availability.markOfflineAndBroadcast(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker unavailable");

        verify(repository).removeShipperLocationCache(7L);
        verifyNoInteractions(webSocketHandler);
    }
}
