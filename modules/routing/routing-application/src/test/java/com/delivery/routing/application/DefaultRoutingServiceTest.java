package com.delivery.routing.application;

import com.delivery.routing.application.api.RoutingProviderPort;
import com.delivery.routing.domain.Coordinate;
import com.delivery.routing.domain.EtaWindowQuery;
import com.delivery.routing.domain.MatrixQuery;
import com.delivery.routing.domain.MatrixResult;
import com.delivery.routing.domain.RouteQuery;
import com.delivery.routing.domain.RouteResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRoutingServiceTest {
    private static final Coordinate ORIGIN = new Coordinate(10.76, 106.66);
    private static final Coordinate DESTINATION = new Coordinate(10.78, 106.68);

    @Test
    void routeFallsBackWhenProviderFails() {
        var service = new DefaultRoutingService(new FailingProvider(), 18);

        RouteResult result = service.route(new RouteQuery("driving", ORIGIN, DESTINATION, null, false));

        assertThat(result.source()).isEqualTo("GEODESIC_FALLBACK");
        assertThat(result.durationSeconds()).isPositive();
        assertThat(result.distanceMeters()).isPositive();
    }

    @Test
    void matrixFallsBackWhenProviderReturnsWrongShape() {
        var service = new DefaultRoutingService(new FailingProvider(), 18);

        List<MatrixResult> result = service.matrix(new MatrixQuery("driving", ORIGIN,
                List.of(new MatrixQuery.Destination("destination", DESTINATION)), null));

        assertThat(result).singleElement().extracting(MatrixResult::source).isEqualTo("GEODESIC_FALLBACK");
    }

    @Test
    void matrixKeepsProviderResultsWhenShapeIsValid() {
        var service = new DefaultRoutingService(new FixedProvider(), 18);

        List<MatrixResult> result = service.matrix(new MatrixQuery("driving", ORIGIN,
                List.of(new MatrixQuery.Destination("destination", DESTINATION)), null));

        assertThat(result).containsExactly(new MatrixResult("destination", 90, 1200, "MAPBOX_MATRIX"));
    }

    @Test
    void etaAddsPreparationAndTenMinuteUncertainty() {
        var service = new DefaultRoutingService(new FixedProvider(), 18);

        var result = service.etaWindow(new EtaWindowQuery(ORIGIN, DESTINATION, 15));

        assertThat(result.minMinutes()).isEqualTo(25);
        assertThat(result.maxMinutes()).isEqualTo(35);
        assertThat(result.source()).isEqualTo("MAPBOX_DIRECTIONS");
    }

    @Test
    void validatesConstructorAndNullInputs() {
        assertThatThrownBy(() -> new DefaultRoutingService(null, 18))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultRoutingService(new FixedProvider(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        var service = new DefaultRoutingService(new FixedProvider(), 18);
        assertThatThrownBy(() -> service.route(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.matrix(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.etaWindow(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fallsBackForNullProviderResponsesAndNullEntries() {
        var service = new DefaultRoutingService(new EmptyProvider(), 18);
        var query = new MatrixQuery("driving", ORIGIN,
                List.of(new MatrixQuery.Destination("destination", DESTINATION)), null);

        assertThat(service.route(new RouteQuery("driving", ORIGIN, DESTINATION, null, false)).source())
                .isEqualTo("GEODESIC_FALLBACK");
        assertThat(service.matrix(query)).singleElement().extracting(MatrixResult::source)
                .isEqualTo("GEODESIC_FALLBACK");

        var nullEntryService = new DefaultRoutingService(new NullEntryProvider(), 18);
        assertThat(nullEntryService.matrix(query)).singleElement().extracting(MatrixResult::source)
                .isEqualTo("GEODESIC_FALLBACK");
    }

    @Test
    void etaRoundsSubMinuteRoutesUpToOneMinute() {
        var service = new DefaultRoutingService(new ShortRouteProvider(), 18);
        var result = service.etaWindow(new EtaWindowQuery(ORIGIN, DESTINATION, 1));
        assertThat(result.minMinutes()).isEqualTo(2);
        assertThat(result.maxMinutes()).isEqualTo(12);
    }

    private static final class FailingProvider implements RoutingProviderPort {
        @Override
        public RouteResult route(RouteQuery query) { throw new IllegalStateException("provider unavailable"); }

        @Override
        public List<MatrixResult> matrix(MatrixQuery query) { throw new IllegalStateException("provider unavailable"); }
    }

    private static final class FixedProvider implements RoutingProviderPort {
        @Override
        public RouteResult route(RouteQuery query) { return new RouteResult(600, 1000, null, "MAPBOX_DIRECTIONS"); }

        @Override
        public List<MatrixResult> matrix(MatrixQuery query) {
            return List.of(new MatrixResult("destination", 90, 1200, "MAPBOX_MATRIX"));
        }
    }

    private static final class EmptyProvider implements RoutingProviderPort {
        @Override
        public RouteResult route(RouteQuery query) { return null; }

        @Override
        public List<MatrixResult> matrix(MatrixQuery query) { return null; }
    }

    private static final class NullEntryProvider implements RoutingProviderPort {
        @Override
        public RouteResult route(RouteQuery query) { return new RouteResult(60, 100, null, "PROVIDER"); }

        @Override
        public List<MatrixResult> matrix(MatrixQuery query) { return java.util.Collections.singletonList(null); }
    }

    private static final class ShortRouteProvider implements RoutingProviderPort {
        @Override
        public RouteResult route(RouteQuery query) { return new RouteResult(0, 0, null, "SHORT"); }

        @Override
        public List<MatrixResult> matrix(MatrixQuery query) { return List.of(); }
    }
}
