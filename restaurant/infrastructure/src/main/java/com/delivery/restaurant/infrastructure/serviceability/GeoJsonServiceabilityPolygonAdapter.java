package com.delivery.restaurant.infrastructure.serviceability;

import com.delivery.restaurant.domain.serviceability.ServiceabilityGeometry;
import com.delivery.restaurant.domain.serviceability.ServiceabilityGeometry.Point;
import com.delivery.restaurant.domain.serviceability.ServiceabilityGeometry.Polygon;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Decodes the supported GeoJSON envelope; domain validates the polygon. */
public final class GeoJsonServiceabilityPolygonAdapter {
    private GeoJsonServiceabilityPolygonAdapter() { }

    public static Polygon parsePolygon(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) {
            throw new IllegalArgumentException("polygonGeoJson is required");
        }
        try {
            JsonNode root = new ObjectMapper().readTree(geoJson);
            if (root == null || !"Polygon".equals(root.path("type").asText())) {
                throw new IllegalArgumentException("Only GeoJSON Polygon is supported");
            }
            JsonNode coordinates = root.get("coordinates");
            if (coordinates == null || !coordinates.isArray() || coordinates.size() != 1) {
                throw new IllegalArgumentException("Polygon must contain exactly one outer ring in v1");
            }
            JsonNode ring = coordinates.get(0);
            if (!ring.isArray() || ring.size() < 4) {
                throw new IllegalArgumentException("Polygon outer ring requires at least four positions");
            }
            List<Point> points = new ArrayList<>();
            for (JsonNode position : ring) {
                if (!position.isArray() || position.size() < 2
                        || !position.get(0).isNumber() || !position.get(1).isNumber()) {
                    throw new IllegalArgumentException("Polygon positions must be numeric [longitude, latitude]");
                }
                double longitude = position.get(0).asDouble();
                double latitude = position.get(1).asDouble();
                ServiceabilityGeometry.requireVietnamCoordinate(longitude, latitude, "polygon vertex");
                points.add(new Point(longitude, latitude));
            }
            return new Polygon(points);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalidJson) {
            throw new IllegalArgumentException("polygonGeoJson is not valid JSON", invalidJson);
        }
    }

}
