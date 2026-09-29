package com.delivery.tracking.application;

import com.delivery.tracking.application.api.LocationEventPort;
import com.delivery.tracking.application.api.LocationStorePort;
import com.delivery.tracking.application.api.UpdateLocationCommand;
import com.delivery.tracking.domain.Coordinate;
import com.delivery.tracking.domain.LocationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultTrackingServiceTest {
    private final RecordingStore store = new RecordingStore();
    private final RecordingEvents events = new RecordingEvents();
    private final Instant fixedNow = Instant.parse("2026-02-03T04:05:06Z");
    private final DefaultTrackingService service = new DefaultTrackingService(store, events,
            Clock.fixed(fixedNow, ZoneOffset.UTC));

    @Test
    void updateBuildsSnapshotPersistsItAndPublishesIt() {
        UpdateLocationCommand command = new UpdateLocationCommand(7,
                new Coordinate(10.77, 106.7), 3.5, 12.0, 90.0, true);

        LocationSnapshot result = service.updateLocation(command);

        assertThat(result.shipperId()).isEqualTo(7);
        assertThat(result.coordinate()).isEqualTo(command.coordinate());
        assertThat(result.online()).isTrue();
        assertThat(result.lastPing()).isEqualTo(fixedNow);
        assertThat(result.updatedAt()).isEqualTo(fixedNow);
        assertThat(store.saved).isSameAs(result);
        assertThat(events.location).isSameAs(result);
        assertThat(events.source).isEqualTo("APPLICATION");
    }

    @Test
    void markOfflineRetainsLastKnownFactsAndPublishesTombstone() {
        LocationSnapshot current = new LocationSnapshot(7, new Coordinate(10.77, 106.7),
                3.5, 12.0, 90.0, true, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
        store.current = current;

        LocationSnapshot result = service.markOffline(7);

        assertThat(result.online()).isFalse();
        assertThat(result.lastPing()).isEqualTo(fixedNow);
        assertThat(result.coordinate()).isEqualTo(current.coordinate());
        assertThat(result.accuracy()).isEqualTo(current.accuracy());
        assertThat(store.saved).isSameAs(result);
        assertThat(events.location).isSameAs(result);
        assertThat(events.source).isEqualTo("OFFLINE_TOMBSTONE");
    }

    @Test
    void markOfflineFailsClosedWhenThereIsNoCurrentLocation() {
        assertThatThrownBy(() -> service.markOffline(7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No location exists for shipper 7");
    }

    @Test
    void validatesDependenciesCommandsAndShipperIdentity() {
        assertThatThrownBy(() -> new DefaultTrackingService(null, events, Clock.systemUTC()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultTrackingService(store, null, Clock.systemUTC()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultTrackingService(store, events, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.updateLocation(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.markOffline(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markOffline(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class RecordingStore implements LocationStorePort {
        private LocationSnapshot current;
        private LocationSnapshot saved;

        @Override
        public void save(LocationSnapshot location) { saved = location; current = location; }

        @Override
        public Optional<LocationSnapshot> findByShipperId(long shipperId) {
            return Optional.ofNullable(current);
        }

        @Override
        public void remove(long shipperId) { current = null; }
    }

    private static final class RecordingEvents implements LocationEventPort {
        private LocationSnapshot location;
        private String source;

        @Override
        public void publish(LocationSnapshot location, String source) {
            this.location = location;
            this.source = source;
        }
    }
}
