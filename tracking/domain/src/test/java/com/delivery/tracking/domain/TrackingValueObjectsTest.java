package com.delivery.tracking.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrackingValueObjectsTest {
    @Test
    void coordinateAcceptsBoundaryValuesAndRejectsOutOfRangeValues() {
        assertEquals(new Coordinate(-90, 180), new Coordinate(-90, 180));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(90.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0, Double.NaN));
    }

    @Test
    void locationSnapshotValidatesIdentityTimestampsAndMeasurements() {
        Instant now = Instant.now();
        LocationSnapshot snapshot = new LocationSnapshot(
                7, new Coordinate(10, 106), 4.5, null, 90.0, true, now, now);
        assertEquals(7, snapshot.shipperId());
        assertThrows(IllegalArgumentException.class, () -> new LocationSnapshot(
                0, new Coordinate(10, 106), null, null, null, true, now, now));
        assertThrows(IllegalArgumentException.class, () -> new LocationSnapshot(
                7, new Coordinate(10, 106), Double.NaN, null, null, true, now, now));
    }

    @Test
    void publisherLeaseProducesRedisFenceValue() {
        assertEquals("3:session", new PublisherLease(7, "session", 3).redisValue());
        assertThrows(IllegalArgumentException.class, () -> new PublisherLease(7, "", 1));
    }

    @Test
    void exercisesIndependentValidationBranches() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0, -181));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0, 181));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(-91, 0));

        Instant now = Instant.now();
        Coordinate point = new Coordinate(10, 106);
        assertThrows(IllegalArgumentException.class, () -> new LocationSnapshot(
                1, null, null, null, null, true, now, now));
        assertThrows(IllegalArgumentException.class, () -> new LocationSnapshot(
                1, point, null, null, null, true, null, now));
        assertThrows(IllegalArgumentException.class, () -> new LocationSnapshot(
                1, point, null, null, null, true, now, null));
        assertThrows(IllegalArgumentException.class, () -> new LocationSnapshot(
                1, point, null, Double.POSITIVE_INFINITY, null, true, now, now));
        assertThrows(IllegalArgumentException.class, () -> new LocationSnapshot(
                1, point, null, null, Double.NaN, true, now, now));

        assertThrows(IllegalArgumentException.class, () -> new PublisherLease(0, "session", 1));
        assertThrows(IllegalArgumentException.class, () -> new PublisherLease(1, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new PublisherLease(1, "session", 0));
    }
}
