package com.delivery.routing.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingValueObjectsTest {
    private final Coordinate point = new Coordinate(10.76, 106.66);

    @Test
    void rejectsInvalidCoordinatesAndQueries() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new RouteQuery("driving", null, point, null, false));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindowQuery(point, point, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatrixQuery("driving", point, List.of(), null));
    }

    @Test
    void copiesMatrixDestinationsAndKeepsResultValues() {
        var destinations = List.of(new MatrixQuery.Destination("dropoff", point));
        var query = new MatrixQuery("driving", point, destinations, null);

        assertEquals(destinations, query.destinations());
        assertEquals(new RouteResult(60, 1000, null, "GEODESIC_FALLBACK"),
                new RouteResult(60, 1000, null, "GEODESIC_FALLBACK"));
        assertEquals(new EtaWindow(15, 25, "GEODESIC_FALLBACK").maxMinutes(), 25);
    }

    @Test
    void validatesAllResultAndBoundaryFields() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0, 181));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindow(-1, 1, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindow(2, 1, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindow(1, 2, ""));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindowQuery(point, point, 241));
        assertThrows(IllegalArgumentException.class, () -> new MatrixQuery("driving", point,
                List.of(new MatrixQuery.Destination("dropoff", null)), null));
        assertThrows(IllegalArgumentException.class, () -> new RouteResult(-1, 1, null, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> new RouteResult(1, 1, null, ""));
        assertThrows(IllegalArgumentException.class, () -> new MatrixResult("", 1, 1, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> new MatrixResult("x", -1, 1, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> new MatrixResult("x", 1, 1, ""));
    }

    @Test
    void exercisesIndependentValidationBranches() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(-91, 0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0, -181));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(90.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindow(0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindow(0, 1, " "));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindowQuery(null, point, 1));
        assertThrows(IllegalArgumentException.class, () -> new EtaWindowQuery(point, null, 1));
        assertEquals(1, new EtaWindowQuery(point, point, 1).prepMinutes());
        assertThrows(IllegalArgumentException.class, () -> new MatrixQuery("driving", null,
                List.of(new MatrixQuery.Destination("dropoff", point)), null));
        assertThrows(IllegalArgumentException.class, () -> new MatrixQuery("driving", point, null, null));
        assertThrows(IllegalArgumentException.class, () -> new MatrixQuery("driving", point,
                java.util.Arrays.asList(new MatrixQuery.Destination("dropoff", point), null), null));
        var tooMany = new java.util.ArrayList<MatrixQuery.Destination>();
        for (int i = 0; i < 26; i++) tooMany.add(new MatrixQuery.Destination("dropoff-" + i, point));
        assertThrows(IllegalArgumentException.class, () -> new MatrixQuery("driving", point, tooMany, null));
        assertThrows(IllegalArgumentException.class, () -> new RouteQuery("driving", point, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new RouteResult(1, -1, null, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> new RouteResult(1, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new MatrixResult(null, 1, 1, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> new MatrixResult("x", 1, -1, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> new MatrixResult("x", 1, 1, null));
    }
}
