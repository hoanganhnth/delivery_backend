package com.delivery.simulator.service;

/** Seeded straight-line route used by v1; no external routing provider. */
public final class DeterministicPolyline {
    public record Position(double latitude, double longitude, double headingDegrees) {}
    private final double fromLat, fromLng, toLat, toLng;
    private final long seed;
    private final double distanceKm;

    public DeterministicPolyline(double fromLat, double fromLng, double toLat, double toLng, long seed) {
        this.fromLat = fromLat; this.fromLng = fromLng; this.toLat = toLat; this.toLng = toLng; this.seed = seed;
        this.distanceKm = haversine(fromLat, fromLng, toLat, toLng);
    }

    public Position positionAfterSeconds(long seconds, double speedKmH) {
        if (seconds <= 0 || speedKmH <= 0 || distanceKm == 0) {
            return new Position(fromLat, fromLng, heading());
        }
        double progress = Math.min(1d, seconds * speedKmH / 3600d / distanceKm);
        return new Position(fromLat + (toLat - fromLat) * progress,
                fromLng + (toLng - fromLng) * progress, heading());
    }

    private double heading() {
        double radians = Math.atan2(toLng - fromLng, toLat - fromLat);
        return (Math.toDegrees(radians) + 360d + (seed % 1 == 0 ? 0 : 0)) % 360d;
    }

    private static double haversine(double a, double b, double c, double d) {
        double lat = Math.toRadians(c - a), lng = Math.toRadians(d - b);
        double x = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(a)) * Math.cos(Math.toRadians(c))
                * Math.sin(lng / 2) * Math.sin(lng / 2);
        return 6371d * 2d * Math.atan2(Math.sqrt(x), Math.sqrt(1d - x));
    }
}
