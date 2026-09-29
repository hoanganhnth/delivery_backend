package com.delivery.routing.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.routing.domain.Coordinate;
import com.delivery.routing.domain.MatrixQuery;
import com.delivery.routing.domain.RouteQuery;
import com.delivery.routing.infrastructure.config.RoutingProperties;
import org.junit.jupiter.api.Test;

class MapboxRoutingProviderAdapterTest {
    @Test
    void absentTokenDoesNotCallProviderAndLetsApplicationFallbackApply() {
        RoutingProperties properties = new RoutingProperties();
        MapboxRoutingProviderAdapter adapter = new MapboxRoutingProviderAdapter(properties);
        var origin = new Coordinate(10.76, 106.66);
        var destination = new Coordinate(10.78, 106.68);

        assertThat(adapter.route(new RouteQuery("driving", origin, destination, null, false))).isNull();
        assertThat(adapter.matrix(new MatrixQuery("driving", origin,
                java.util.List.of(new MatrixQuery.Destination("d1", destination)), null))).isNull();
    }
}
