package com.delivery.restaurant_service.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceabilityGeometryTest {

    private static final String SQUARE = """
            {"type":"Polygon","coordinates":[[[106.60,10.70],[106.70,10.70],
            [106.70,10.80],[106.60,10.80],[106.60,10.70]]]}""";

    @Test
    void containsInteriorExteriorAndBoundary() {
        var polygon = ServiceabilityGeometry.parsePolygon(SQUARE);

        assertThat(ServiceabilityGeometry.contains(polygon, 106.65, 10.75)).isTrue();
        assertThat(ServiceabilityGeometry.contains(polygon, 106.75, 10.75)).isFalse();
        assertThat(ServiceabilityGeometry.contains(polygon, 106.60, 10.75)).isTrue();
    }

    @Test
    void acceptsAClosedTriangleAsTheSmallestValidPolygon() {
        var polygon = ServiceabilityGeometry.parsePolygon(
                "{\"type\":\"Polygon\",\"coordinates\":[[[106.60,10.70],[106.70,10.70],[106.65,10.80],[106.60,10.70]]]}" );
        assertThat(ServiceabilityGeometry.contains(polygon, 106.65, 10.73)).isTrue();
    }

    @Test
    void rejectsOpenRingsHolesAndOutOfBoundsVertices() {
        assertThatThrownBy(() -> ServiceabilityGeometry.parsePolygon(
                "{\"type\":\"Polygon\",\"coordinates\":[[[106.6,10.7],[106.7,10.7],[106.7,10.8],[106.6,10.8]]]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closed");
        assertThatThrownBy(() -> ServiceabilityGeometry.parsePolygon(
                "{\"type\":\"Polygon\",\"coordinates\":[[[106.6,10.7],[106.7,10.7],[106.7,10.8],[106.6,10.8],[106.6,10.7]],[[106.62,10.72],[106.63,10.72],[106.63,10.73],[106.62,10.72]]]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> ServiceabilityGeometry.parsePolygon(
                "{\"type\":\"Polygon\",\"coordinates\":[[[101.9,10.7],[106.7,10.7],[106.7,10.8],[101.9,10.7]]]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vietnam");
    }
}
