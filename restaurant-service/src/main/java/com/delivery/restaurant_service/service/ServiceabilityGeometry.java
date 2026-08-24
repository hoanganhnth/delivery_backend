package com.delivery.restaurant_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure WGS84 polygon validation and point-in-polygon policy for v1 zones. */
public final class ServiceabilityGeometry {

    public static final double MIN_LATITUDE = 8.0;
    public static final double MAX_LATITUDE = 24.0;
    public static final double MIN_LONGITUDE = 102.0;
    public static final double MAX_LONGITUDE = 110.0;
    private static final double EPSILON = 1e-9;

    private ServiceabilityGeometry() {
    }

    public record Point(double longitude, double latitude) {
    }

    public record Polygon(List<Point> outerRing) {
        public Polygon {
            outerRing = List.copyOf(outerRing);
        }
    }

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
                requireVietnamCoordinate(longitude, latitude, "polygon vertex");
                points.add(new Point(longitude, latitude));
            }
            Point first = points.get(0);
            Point last = points.get(points.size() - 1);
            if (!samePoint(first, last)) {
                throw new IllegalArgumentException("Polygon outer ring must be closed");
            }
            Set<String> distinct = new HashSet<>();
            points.forEach(point -> distinct.add(point.longitude() + ":" + point.latitude()));
            if (distinct.size() < 3 || Math.abs(signedArea(points)) < EPSILON) {
                throw new IllegalArgumentException("Polygon outer ring must enclose an area");
            }
            return new Polygon(points);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalidJson) {
            throw new IllegalArgumentException("polygonGeoJson is not valid JSON", invalidJson);
        }
    }

    public static boolean contains(Polygon polygon, double longitude, double latitude) {
        requireVietnamCoordinate(longitude, latitude, "delivery coordinate");
        List<Point> ring = polygon.outerRing();
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            Point a = ring.get(j);
            Point b = ring.get(i);
            if (onSegment(a, b, longitude, latitude)) return true;
            boolean crosses = (a.latitude() > latitude) != (b.latitude() > latitude);
            if (crosses) {
                double intersectionLongitude = (b.longitude() - a.longitude())
                        * (latitude - a.latitude()) / (b.latitude() - a.latitude()) + a.longitude();
                if (longitude < intersectionLongitude) inside = !inside;
            }
        }
        return inside;
    }

    public static void requireVietnamCoordinate(double longitude, double latitude, String label) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE
                || latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new IllegalArgumentException(label + " must be a finite coordinate in Vietnam bounds");
        }
    }

    private static boolean onSegment(Point a, Point b, double longitude, double latitude) {
        double cross = (b.longitude() - a.longitude()) * (latitude - a.latitude())
                - (b.latitude() - a.latitude()) * (longitude - a.longitude());
        if (Math.abs(cross) > EPSILON) return false;
        return longitude >= Math.min(a.longitude(), b.longitude()) - EPSILON
                && longitude <= Math.max(a.longitude(), b.longitude()) + EPSILON
                && latitude >= Math.min(a.latitude(), b.latitude()) - EPSILON
                && latitude <= Math.max(a.latitude(), b.latitude()) + EPSILON;
    }

    private static double signedArea(List<Point> points) {
        double area = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            Point a = points.get(i);
            Point b = points.get(i + 1);
            area += a.longitude() * b.latitude() - b.longitude() * a.latitude();
        }
        return area / 2;
    }

    private static boolean samePoint(Point a, Point b) {
        return Math.abs(a.longitude() - b.longitude()) <= EPSILON
                && Math.abs(a.latitude() - b.latitude()) <= EPSILON;
    }
}
