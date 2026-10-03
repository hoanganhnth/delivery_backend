package com.delivery.tracking.application;
import com.delivery.tracking.application.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DefaultDeliveryRoomSubscriptionUseCaseTest {
    private final Ports ports=new Ports();
    private final DefaultDeliveryRoomSubscriptionUseCase core=new DefaultDeliveryRoomSubscriptionUseCase(ports,ports);
    @Test void authorizedBatchScopeSynchronizesAllAssignedRoomsBeforeMembership() {
        ports.active=Set.of(100L,101L); core.subscribeAuthorized(100,42,"session");
        assertThat(ports.synced).isEqualTo(ports.active);
        assertThat(ports.calls).containsExactly("read","sync","subscribe");
    }
    @Test void absentOrStaleProjectionUsesOnlyTheAuthorizedDeliveryAsFallback() {
        for(Set<Long> active:List.of(Set.<Long>of(),Set.of(999L))) {
            ports.active=active; core.subscribeAuthorized(100,42,"session");
            assertThat(ports.synced).containsExactly(100L);
        }
    }
    @Test void invalidScopeAndSessionNeverReadOrMutateIndex() {
        assertThatThrownBy(()->core.subscribeAuthorized(0,42,"session")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->core.subscribeAuthorized(100,0,"session")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->core.subscribeAuthorized(100,42,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->core.subscribeAuthorized(100,42," ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ports.calls).isEmpty();
    }
    @Test void projectionFailureRemainsVisibleWithoutMembershipMutation() {
        ports.failure=new IllegalStateException("Redis unavailable");
        assertThatThrownBy(()->core.subscribeAuthorized(100,42,"session")).isSameAs(ports.failure);
        assertThat(ports.calls).containsExactly("read");
    }
    @Test void dependenciesCannotBeMissing() {
        assertThatThrownBy(()->new DefaultDeliveryRoomSubscriptionUseCase(null,ports)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(()->new DefaultDeliveryRoomSubscriptionUseCase(ports,null)).isInstanceOf(NullPointerException.class);
    }
    private static class Ports implements DeliveryRoomAssignmentPort,DeliveryRoomIndexPort {
        Set<Long> active=Set.of(),synced; RuntimeException failure; final List<String> calls=new ArrayList<>();
        public Set<Long> activeDeliveries(long shipper){calls.add("read");if(failure!=null)throw failure;return active;}
        public void synchronize(long shipper,Set<Long> deliveries){assertThat(shipper).isEqualTo(42);calls.add("sync");synced=Set.copyOf(deliveries);}
        public void subscribe(long delivery,long shipper,String session){assertThat(delivery).isEqualTo(100);assertThat(shipper).isEqualTo(42);assertThat(session).isEqualTo("session");calls.add("subscribe");}
        public void activate(long delivery,long shipper){throw new AssertionError();}
        public void end(long delivery,long shipper){throw new AssertionError();}
        public void busy(long shipper,long delivery,long time,String id){throw new AssertionError();}
        public void busyBatch(long shipper,long delivery,long time,String id){throw new AssertionError();}
        public void available(long shipper,long delivery,long time){throw new AssertionError();}
        public void availableBatch(long shipper,long delivery,long time){throw new AssertionError();}
    }
}
