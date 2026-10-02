package com.delivery.restaurant.domain.serviceability;

import com.delivery.restaurant.domain.serviceability.ServiceabilityGeometry.Point;
import com.delivery.restaurant.domain.serviceability.ServiceabilityGeometry.Polygon;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceabilityGeometryTest {
    private static final Point A = new Point(106.6, 10.7);
    private static final Point B = new Point(106.7, 10.7);
    private static final Point C = new Point(106.7, 10.8);
    private static final Point D = new Point(106.6, 10.8);

    @Test void includesInteriorEdgesAndVerticesButExcludesExterior() {
        var polygon = new Polygon(List.of(A, B, C, D, A));
        for (double[] point : List.of(new double[]{106.65, 10.75},
                new double[]{106.6, 10.75}, new double[]{106.7, 10.75},
                new double[]{106.65, 10.7}, new double[]{106.65, 10.8},
                new double[]{106.6, 10.7}, new double[]{106.7, 10.8})) {
            assertTrue(ServiceabilityGeometry.contains(polygon, point[0], point[1]));
        }
        for (double[] point : List.of(new double[]{106.5, 10.75}, new double[]{106.8, 10.75},
                new double[]{106.65, 10.6}, new double[]{106.65, 10.9}, new double[]{106.8, 10.8})) {
            assertFalse(ServiceabilityGeometry.contains(polygon, point[0], point[1]));
        }
    }

    @Test void supportsBothRingOrientationsAndConcavePolygons() {
        var clockwise = new Polygon(List.of(A, D, C, B, A));
        assertTrue(ServiceabilityGeometry.contains(clockwise, 106.65, 10.75));
        var concave = new Polygon(List.of(A, B, C, new Point(106.65, 10.75), D, A));
        assertTrue(ServiceabilityGeometry.contains(concave, 106.65, 10.73));
        assertFalse(ServiceabilityGeometry.contains(concave, 106.65, 10.79));
    }

    @Test void validatesClosureMinimumVerticesAndArea() {
        assertThrows(IllegalArgumentException.class, () -> new Polygon(List.of(A, B, A)));
        assertThrows(IllegalArgumentException.class, () -> new Polygon(List.of(A, B, C, D)));
        assertThrows(IllegalArgumentException.class, () -> new Polygon(List.of(A, B, C, new Point(106.6, 10.71))));
        assertThrows(IllegalArgumentException.class, () -> new Polygon(List.of(A, A, A, A)));
        assertThrows(IllegalArgumentException.class, () -> new Polygon(List.of(A, B, new Point(106.8, 10.7), A)));
        assertDoesNotThrow(() -> new Polygon(List.of(A, B, C, A)));
    }

    @Test void rejectsNonFiniteAndOutOfBoundsCoordinatesAtBothEntryPoints() {
        var polygon = new Polygon(List.of(A, B, C, D, A));
        for (double[] point : List.of(new double[]{Double.NaN, 10.7},
                new double[]{106.6, Double.POSITIVE_INFINITY}, new double[]{101.9, 10.7},
                new double[]{110.1, 10.7}, new double[]{106.6, 7.9}, new double[]{106.6, 24.1})) {
            assertThrows(IllegalArgumentException.class,
                    () -> ServiceabilityGeometry.contains(polygon, point[0], point[1]));
            assertThrows(IllegalArgumentException.class,
                    () -> new Polygon(List.of(A, B, new Point(point[0], point[1]), A)));
        }
        assertDoesNotThrow(() -> ServiceabilityGeometry.requireVietnamCoordinate(102, 8, "coordinate"));
        assertDoesNotThrow(() -> ServiceabilityGeometry.requireVietnamCoordinate(110, 24, "coordinate"));
    }

    @Test void snapshotsTheRingAndDoesNotExposeMutableCoordinates() {
        var ring = new ArrayList<>(List.of(A, B, C, D, A));
        var polygon = new Polygon(ring);
        ring.clear();
        assertEquals(5, polygon.outerRing().size());
        assertThrows(UnsupportedOperationException.class, () -> polygon.outerRing().clear());
    }
}
