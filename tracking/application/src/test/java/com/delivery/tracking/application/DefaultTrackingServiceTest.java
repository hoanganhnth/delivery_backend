package com.delivery.tracking.application;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultTrackingServiceTest {
    @Test void fencedPublisherSkipsPublicationAndCannotUseUnfencedWebSocketOrAnotherShipperCommand() {
        var f = new Fixture(); f.admitted = false;
        var lease = new PublisherLease(7, "current", 1);
        assertThat(f.core.updatePublisherLocation(command(LocationUpdateSource.WEBSOCKET), lease)).isEmpty();
        assertThat(f.steps).containsExactly("save"); assertThat(f.saved).isNull(); assertThat(f.published).isNull(); assertThat(f.broadcast).isNull();
        f.steps.clear();
        assertThatThrownBy(() -> f.core.updateLocation(command(LocationUpdateSource.WEBSOCKET))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.core.updatePublisherLocation(command(LocationUpdateSource.APPLICATION), lease)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.core.updatePublisherLocation(command(LocationUpdateSource.WEBSOCKET), new PublisherLease(8, "wrong", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.core.updatePublisherLocation(null, lease)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> f.core.updatePublisherLocation(command(LocationUpdateSource.WEBSOCKET), null)).isInstanceOf(NullPointerException.class);
        assertThat(f.steps).isEmpty();
    }
    @Test void applicationUpdateKeepsCanonicalFactsAndSaveFanoutPublishOrder() {
        var f = new Fixture(); var result = f.core.updateLocation(command(LocationUpdateSource.APPLICATION));
        assertThat(result.shipperId()).isEqualTo(7); assertThat(result.coordinate()).isEqualTo(new Coordinate(10.77, 106.7));
        assertThat(result.online()).isTrue(); assertThat(result.accuracy()).isEqualTo(3.5);
        assertThat(result.speed()).isEqualTo(12.0); assertThat(result.heading()).isEqualTo(90.0);
        assertThat(result.lastPing()).isEqualTo(f.now); assertThat(result.updatedAt()).isEqualTo(f.now);
        assertThat(f.saved).isSameAs(result); assertThat(f.published).isSameAs(result); assertThat(f.broadcast).isSameAs(result);
        assertThat(f.source).isEqualTo(LocationUpdateSource.APPLICATION);
        assertThat(f.steps).containsExactly("save", "broadcast", "publish");
    }
    @Test void webSocketUpdateKeepsSavePublishFanoutOrderAndUnknownTelemetry() {
        var f = new Fixture(); var result = f.core.updatePublisherLocation(new UpdateLocationCommand(7, new Coordinate(10.77, 106.7),
                null, null, null, false, LocationUpdateSource.WEBSOCKET), new PublisherLease(7, "current", 1)).orElseThrow();
        assertThat(result.online()).isFalse(); assertThat(result.accuracy()).isNull();
        assertThat(result.speed()).isNull(); assertThat(result.heading()).isNull();
        assertThat(f.saved).isSameAs(result); assertThat(f.published).isSameAs(result); assertThat(f.broadcast).isSameAs(result);
        assertThat(f.source).isEqualTo(LocationUpdateSource.WEBSOCKET);
        assertThat(f.steps).containsExactly("save", "publish", "broadcast");
    }
    @Test void persistenceFailureCannotPublishOrFanoutForEitherSource() {
        for (var source : LocationUpdateSource.values()) {
            var f = new Fixture(); f.failAt = "save";
            assertThatThrownBy(() -> update(f, source)).isSameAs(f.failure);
            assertThat(f.steps).containsExactly("save"); assertThat(f.published).isNull(); assertThat(f.broadcast).isNull();
        }
    }
    @Test void brokerFailureRemainsVisibleAndWebSocketCannotFanoutBeforeKafka() {
        for (var source : LocationUpdateSource.values()) {
            var f = new Fixture(); f.failAt = "publish";
            assertThatThrownBy(() -> update(f, source)).isSameAs(f.failure);
            assertThat(f.saved).isNotNull();
            if (source == LocationUpdateSource.WEBSOCKET) assertThat(f.steps).containsExactly("save", "publish");
            else assertThat(f.steps).containsExactly("save", "broadcast", "publish");
        }
    }
    @Test void unexpectedFanoutFailurePropagatesAtTheEstablishedPointInEachPipeline() {
        for (var source : LocationUpdateSource.values()) {
            var f = new Fixture(); f.failAt = "broadcast";
            assertThatThrownBy(() -> update(f, source)).isSameAs(f.failure);
            if (source == LocationUpdateSource.WEBSOCKET) assertThat(f.steps).containsExactly("save", "publish", "broadcast");
            else assertThat(f.steps).containsExactly("save", "broadcast");
        }
    }
    @Test void invalidTelemetryIdentityOrCoordinatesAreRejectedBeforeSideEffects() {
        for (int measurement = 0; measurement < 3; measurement++) {
            var f = new Fixture(); int index = measurement;
            assertThatThrownBy(() -> f.core.updateLocation(new UpdateLocationCommand(7, new Coordinate(10, 106),
                    index == 0 ? Double.NaN : null, index == 1 ? Double.POSITIVE_INFINITY : null,
                    index == 2 ? Double.NEGATIVE_INFINITY : null, true, LocationUpdateSource.APPLICATION))).isInstanceOf(IllegalArgumentException.class);
            assertThat(f.steps).isEmpty();
        }
        var f = new Fixture();
        assertThatThrownBy(() -> f.core.updateLocation(new UpdateLocationCommand(0, new Coordinate(10, 106), null, null, null, true, LocationUpdateSource.APPLICATION)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.core.updateLocation(new UpdateLocationCommand(7, null, null, null, null, true, LocationUpdateSource.APPLICATION)))
                .isInstanceOf(IllegalArgumentException.class); assertThat(f.steps).isEmpty();
    }
    @Test void validatesDependenciesCommandAndTrustedSource() {
        var f = new Fixture();
        assertThatThrownBy(() -> new DefaultTrackingService(null, f)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultTrackingService(f, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultTrackingService(f, f, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> f.core.updateLocation(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> f.core.updateLocation(new UpdateLocationCommand(7, new Coordinate(10, 106), null, null, null, true, null)))
                .isInstanceOf(NullPointerException.class);
    }
    private LocationSnapshot update(Fixture f, LocationUpdateSource source) {
        return source == LocationUpdateSource.APPLICATION ? f.core.updateLocation(command(source))
                : f.core.updatePublisherLocation(command(source), new PublisherLease(7, "current", 1)).orElseThrow();
    }
    private UpdateLocationCommand command(LocationUpdateSource source) {
        return new UpdateLocationCommand(7, new Coordinate(10.77, 106.7), 3.5, 12.0, 90.0, true, source);
    }
    private static final class Fixture implements LocationStorePort, LocationEventPort {
        final Instant now = Instant.parse("2026-02-03T04:05:06Z");
        final DefaultTrackingService core = new DefaultTrackingService(this, this, Clock.fixed(now, ZoneOffset.UTC));
        LocationSnapshot saved, published, broadcast; LocationUpdateSource source; String failAt;
        boolean admitted = true;
        final RuntimeException failure = new IllegalStateException("Boundary unavailable"); final List<String> steps = new ArrayList<>();
        void step(String step) { steps.add(step); if (step.equals(failAt)) throw failure; }
        public void save(LocationSnapshot value, LocationUpdateSource source) { step("save"); saved = value; this.source = source; }
        public boolean saveIfCurrentPublisher(LocationSnapshot value, PublisherLease lease) {
            step("save");
            if (!admitted) return false;
            saved = value; source = LocationUpdateSource.WEBSOCKET; return true;
        }
        public void publish(LocationSnapshot value, LocationUpdateSource source) { step("publish"); published = value; this.source = source; }
        public void broadcast(LocationSnapshot value, LocationUpdateSource source) { step("broadcast"); broadcast = value; this.source = source; }
    }
}
