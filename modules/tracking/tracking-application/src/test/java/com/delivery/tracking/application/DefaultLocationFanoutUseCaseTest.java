package com.delivery.tracking.application;
import com.delivery.tracking.application.api.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DefaultLocationFanoutUseCaseTest {
    private final Ports ports = new Ports();
    private final DefaultLocationFanoutUseCase core = new DefaultLocationFanoutUseCase(ports, ports);
    private FanoutLocation location(Long shipperId) {
        return new FanoutLocation(shipperId, 10.77, 106.7, 4.25, 8.5, 180.0, true, "ping", "updated", 1.2);
    }
    @Test void batchPublishesOnlyExactAssignedRoomsWithoutLegacyReadOrChangingFacts() {
        ports.active = new LinkedHashSet<>(List.of(100L, 101L)); var location = location(42L);
        core.publish(location);
        assertThat(ports.attempted).containsExactly(100L, 101L);
        assertThat(ports.locations).containsExactly(location, location);
        assertThat(ports.legacyReads).isZero();
    }
    @Test void nullAndEmptyMultiAssignmentUseLegacyFallback() {
        for (Set<Long> active : Arrays.asList(null, Set.<Long>of())) {
            ports.active=active; ports.legacy=Optional.of(100L); core.publish(location(42L));
        }
        assertThat(ports.attempted).containsExactly(100L, 100L);
        assertThat(ports.legacyReads).isEqualTo(2);
    }
    @Test void missingAssignmentPublishesNothingAndMissingLocationNeverReadsProjection() {
        core.publish(location(42L)); assertThat(ports.attempted).isEmpty();
        ports.reads=0; core.publish(null); core.publish(location(null));
        assertThat(ports.reads).isZero();
    }
    @Test void checkedAndRuntimePublishFailureAreReportedAndOtherRoomsStillReceive() {
        ports.active = new LinkedHashSet<>(List.of(100L,101L,102L));
        ports.failures.put(100L, new IOException("JSON mapping failure"));
        ports.failures.put(101L, new IllegalStateException("PubSub unavailable"));
        core.publish(location(42L));
        assertThat(ports.attempted).containsExactly(100L,101L,102L);
        assertThat(ports.incidents).containsExactly(ports.failures.get(100L), ports.failures.get(101L));
        assertThat(ports.incidentShippers).containsExactly(42L,42L);
    }
    @Test void routingReadFailureRemainsVisibleBeforeAnyPublish() {
        var failure = new IllegalStateException("projection unavailable"); ports.readFailure=failure;
        assertThatThrownBy(() -> core.publish(location(42L))).isSameAs(failure);
        assertThat(ports.attempted).isEmpty(); assertThat(ports.incidents).isEmpty();
    }
    @Test void offlineIdentityOnlyFactsAreNotDiscardedOrRewritten() {
        ports.legacy=Optional.of(100L);
        var offline = new FanoutLocation(42L,null,null,null,null,null,false,"ping","updated",null);
        core.publish(offline); assertThat(ports.locations).containsExactly(offline);
    }
    @Test void dependenciesCannotBeMissing() {
        assertThatThrownBy(() -> new DefaultLocationFanoutUseCase(null,ports)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultLocationFanoutUseCase(ports,null)).isInstanceOf(NullPointerException.class);
    }
    private static final class Ports implements FanoutDeliveryReadPort, LocationFanoutEventPort {
        Set<Long> active=Set.of(); Optional<Long> legacy=Optional.empty(); int reads,legacyReads;
        RuntimeException readFailure;
        final List<Long> attempted=new ArrayList<>(), incidentShippers=new ArrayList<>();
        final List<FanoutLocation> locations=new ArrayList<>();
        final List<Exception> incidents=new ArrayList<>();
        final Map<Long,Exception> failures=new HashMap<>();
        public Set<Long> activeDeliveries(Long shipper) { reads++; if(readFailure!=null)throw readFailure; return active; }
        public Optional<Long> activeDelivery(Long shipper) { legacyReads++; return legacy; }
        public void publish(Long delivery,FanoutLocation location) throws Exception {
            attempted.add(delivery); locations.add(location); if(failures.containsKey(delivery))throw failures.get(delivery);
        }
        public void failed(Long shipper,Exception error) { incidentShippers.add(shipper); incidents.add(error); }
    }
}
