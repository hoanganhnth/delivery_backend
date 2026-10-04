package com.delivery.match_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.match_service.entity.DispatchPoolItem;
import com.delivery.routing.contracts.Coordinate;
import com.delivery.routing.contracts.RouteRequest;
import com.delivery.routing.contracts.RouteResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoutingClientTest {

    @Test
    void usesTypedRoutingClientAndContractsForEachLeg() {
        com.delivery.routing.client.RoutingClient platformClient =
                mock(com.delivery.routing.client.RoutingClient.class);
        when(platformClient.getRoute(any(RouteRequest.class)))
                .thenReturn(new RouteResponse(10, 100, null, "TEST"))
                .thenReturn(new RouteResponse(20, 200, null, "TEST"));

        RoutingClient client = new RoutingClient(platformClient);
        DispatchPoolItem item = item(10.78, 106.70, 10.79, 106.71);

        RoutingClient.RoutePlan result = client.planRoute(10.77, 106.69, List.of(item));

        assertThat(result.durationSeconds()).isEqualTo(30);
        assertThat(result.orderedItems()).containsExactly(item);

        ArgumentCaptor<RouteRequest> requests = ArgumentCaptor.forClass(RouteRequest.class);
        verify(platformClient, times(2)).getRoute(requests.capture());
        assertThat(requests.getAllValues().get(0)).isEqualTo(new RouteRequest(
                "driving-traffic",
                new Coordinate(10.77, 106.69),
                new Coordinate(10.78, 106.70),
                null,
                false));
        assertThat(requests.getAllValues().get(1)).isEqualTo(new RouteRequest(
                "driving-traffic",
                new Coordinate(10.78, 106.70),
                new Coordinate(10.79, 106.71),
                null,
                false));
    }

    @Test
    void cachesGeodesicFallbackLegsAfterRoutingFailure() {
        com.delivery.routing.client.RoutingClient platformClient =
                mock(com.delivery.routing.client.RoutingClient.class);
        when(platformClient.getRoute(any(RouteRequest.class)))
                .thenThrow(new RuntimeException("routing unavailable"));

        RoutingClient client = new RoutingClient(platformClient);
        DispatchPoolItem item = item(10.78, 106.70, 10.79, 106.71);

        long first = client.estimateRouteSeconds(10.77, 106.69, List.of(item));
        long second = client.estimateRouteSeconds(10.77, 106.69, List.of(item));

        assertThat(first).isPositive().isEqualTo(second);
        verify(platformClient, times(2)).getRoute(any(RouteRequest.class));
    }

    @Test
    void saturatesRouteTotalsInsteadOfOverflowing() {
        com.delivery.routing.client.RoutingClient platformClient =
                mock(com.delivery.routing.client.RoutingClient.class);
        when(platformClient.getRoute(any(RouteRequest.class)))
                .thenReturn(new RouteResponse(Long.MAX_VALUE, 0, null, "TEST"));

        RoutingClient client = new RoutingClient(platformClient);

        assertThat(client.estimateRouteSeconds(
                10.77, 106.69, List.of(item(10.78, 106.70, 10.79, 106.71))))
                .isEqualTo(Long.MAX_VALUE);
    }

    private DispatchPoolItem item(double pickupLat, double pickupLng,
                                   double deliveryLat, double deliveryLng) {
        DispatchPoolItem item = new DispatchPoolItem();
        item.setPoolItemId(UUID.randomUUID());
        item.setPickupLat(pickupLat);
        item.setPickupLng(pickupLng);
        item.setDeliveryLat(deliveryLat);
        item.setDeliveryLng(deliveryLng);
        return item;
    }
}
