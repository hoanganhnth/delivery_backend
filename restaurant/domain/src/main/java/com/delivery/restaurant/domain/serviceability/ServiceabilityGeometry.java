package com.delivery.restaurant.domain.serviceability;

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
            if (outerRing.size() < 4) {
                throw new IllegalArgumentException("Polygon outer ring requires at least four positions");
            }
            for (Point point : outerRing) {
                requireVietnamCoordinate(point.longitude(), point.latitude(), "polygon vertex");
            }
            if (!samePoint(outerRing.get(0), outerRing.get(outerRing.size() - 1))) {
                throw new IllegalArgumentException("Polygon outer ring must be closed");
            }
            Set<String> distinct = new HashSet<>();
            outerRing.forEach(point -> distinct.add(point.longitude() + ":" + point.latitude()));
            if (distinct.size() < 3 || Math.abs(signedArea(outerRing)) < EPSILON) {
                throw new IllegalArgumentException("Polygon outer ring must enclose an area");
            }
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
