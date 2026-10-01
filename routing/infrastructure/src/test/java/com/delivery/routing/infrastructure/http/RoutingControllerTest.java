package com.delivery.routing.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.routing.application.api.RoutingPort;
import com.delivery.routing.contracts.Coordinate;
import com.delivery.routing.contracts.EtaWindowRequest;
import com.delivery.routing.contracts.MatrixRequest;
import com.delivery.routing.domain.EtaWindow;
import com.delivery.routing.domain.EtaWindowQuery;
import com.delivery.routing.domain.MatrixQuery;
import com.delivery.routing.domain.MatrixResult;
import com.delivery.routing.domain.RouteQuery;
import com.delivery.routing.domain.RouteResult;
import com.delivery.routing.infrastructure.config.RoutingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingControllerTest {

    @Test
    void mapsWireRequestsThroughApplicationPortAndPreservesResponses() {
        RoutingPort port = new RoutingPort() {
            @Override
            public RouteResult route(RouteQuery query) {
                return new RouteResult(120, 900, "geometry", "TEST");
            }

            @Override
            public List<MatrixResult> matrix(MatrixQuery query) {
                return List.of(new MatrixResult("destination", 60, 450, "TEST"));
            }

            @Override
            public EtaWindow etaWindow(EtaWindowQuery query) {
                return new EtaWindow(20, 30, "TEST");
            }
        };
        RoutingProperties properties = new RoutingProperties();
        properties.setInternalSecret("secret");
        RoutingController controller = new RoutingController(port, properties);

        var matrix = controller.matrix("secret", new MatrixRequest("driving",
                new Coordinate(10.76, 106.66),
                List.of(new MatrixRequest.Destination("destination", new Coordinate(10.78, 106.68))), null));
        var eta = controller.etaWindow("secret", new EtaWindowRequest(
                new Coordinate(10.76, 106.66), new Coordinate(10.78, 106.68), 15));

        assertThat(matrix.getBody().results()).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo("destination");
            assertThat(result.durationSeconds()).isEqualTo(60);
            assertThat(result.source()).isEqualTo("TEST");
        });
        assertThat(eta.getBody()).extracting("minMinutes", "maxMinutes", "source")
                .containsExactly(20, 30, "TEST");
    }
}
