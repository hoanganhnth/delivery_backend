package com.delivery.tracking.domain;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LocationHistoryPolicyTest {
    @Test void tenSecondBoundaryAndMovementThresholdApplyInBothTimeDirections() {
        Instant t = Instant.parse("2026-10-03T00:00:00Z");
        var lat = new BigDecimal("10.77000"); var lon = new BigDecimal("106.70000");
        assertFalse(LocationHistoryPolicy.separated(t, lat, lon, t.plusSeconds(9), lat, lon));
        assertTrue(LocationHistoryPolicy.separated(t, lat, lon, t.plusSeconds(10), lat, lon));
        assertTrue(LocationHistoryPolicy.separated(t, lat, lon, t.minusSeconds(10), lat, lon));
        assertFalse(LocationHistoryPolicy.separated(t, lat, lon, t.plusSeconds(1), new BigDecimal("10.77010"), lon));
        assertTrue(LocationHistoryPolicy.separated(t, lat, lon, t.plusSeconds(1), new BigDecimal("10.77030"), lon));
    }
    @Test void precisionOptionalTelemetryAndSourceRetainApprovedNormalization() {
        assertEquals(new BigDecimal("10.77001"), LocationHistoryPolicy.coordinate(10.770006));
        assertEquals(new BigDecimal("-10.77001"), LocationHistoryPolicy.coordinate(-10.770005));
        assertEquals(new BigDecimal("4.26"), LocationHistoryPolicy.telemetry(4.255));
        assertNull(LocationHistoryPolicy.telemetry(null));
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> LocationHistoryPolicy.telemetry(bad));
        assertEquals("UNKNOWN", LocationHistoryPolicy.normalizedSource(null));
        assertEquals("UNKNOWN", LocationHistoryPolicy.normalizedSource(" "));
        assertEquals("WEBSOCKET", LocationHistoryPolicy.normalizedSource("WEBSOCKET"));
        assertEquals("a".repeat(32), LocationHistoryPolicy.normalizedSource("a".repeat(40)));
    }
}
