package com.delivery.tracking.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
/** Approved ten-second/25-metre sampling and bounded precision/source policy. */
public final class LocationHistoryPolicy {
    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(10);
    private static final double SAMPLE_DISTANCE_METRES = 25.0;
    private LocationHistoryPolicy() {}
    public static boolean separated(Instant adjacentTime, BigDecimal adjacentLatitude, BigDecimal adjacentLongitude,
            Instant occurredAt, BigDecimal latitude, BigDecimal longitude) {
        Duration elapsed = Duration.between(adjacentTime, occurredAt).abs();
        return elapsed.compareTo(SAMPLE_INTERVAL) >= 0
                || distanceMetres(adjacentLatitude.doubleValue(), adjacentLongitude.doubleValue(),
                                  latitude.doubleValue(), longitude.doubleValue()) >= SAMPLE_DISTANCE_METRES;
    }

    private static double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double latDelta = Math.toRadians(lat2 - lat1);
        double lonDelta = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
        return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static BigDecimal coordinate(Double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP);
    }

    public static BigDecimal telemetry(Double value) {
        if (value == null) return null;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Telemetry must be finite");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static String normalizedSource(String source) {
        if (source == null || source.isBlank()) return "UNKNOWN";
        return source.substring(0, Math.min(source.length(), 32));
    }

}
