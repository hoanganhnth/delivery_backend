package com.delivery.tracking.application;
import com.delivery.tracking.application.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DefaultDeliveryRoomAssignmentUseCaseTest {
    private final Ports ports=new Ports();
    private final DefaultDeliveryRoomAssignmentUseCase core=new DefaultDeliveryRoomAssignmentUseCase(ports,ports);
    private final String id="00000000-0000-0000-0000-000000000001";
    private DeliveryRoomAssignmentCommand event(long shipper,long delivery,long order,long time,String id,String status,boolean batch) {
        return new DeliveryRoomAssignmentCommand(shipper,delivery,order,time,id,status,batch);
    }
    private DeliveryRoomAssignmentCommand valid(String status,boolean batch) { return event(42,100,7,2000,id,status,batch); }
    @Test void busyAndAvailableDispatchSingleAndBatchBeforeUpdatingLocalRooms() {
        ports.active=Set.of(100L);
        core.apply(valid("busy",false)); core.apply(valid("BUSY",true));
        ports.active=Set.of();
        core.apply(valid("available",false)); core.apply(valid("AVAILABLE",true));
        assertThat(ports.calls).containsExactly("busy","read","activate","busyBatch","read","sync",
                "available","read","end","availableBatch","read","sync");
    }
    @Test void staleBusyCannotReplaceCurrentRoomAndStaleAvailableCannotEndIt() {
        ports.active=Set.of(200L); core.apply(valid("BUSY",false));
        ports.active=Set.of(100L); core.apply(valid("AVAILABLE",false));
        assertThat(ports.calls).containsExactly("busy","read","available","read");
    }
    @Test void invalidIdentityUuidAndStatusFailBeforeAnyProjectionOrRoomMutation() {
        var invalid=List.of(event(0,100,7,2000,id,"BUSY",false),event(42,0,7,2000,id,"BUSY",false),
                event(42,100,0,2000,id,"BUSY",false),event(42,100,7,0,id,"BUSY",false),
                event(42,100,7,2000,null,"BUSY",false),event(42,100,7,2000," ","BUSY",false),
                event(42,100,7,2000,"bad","BUSY",false),event(42,100,7,2000,id,null,false),
                event(42,100,7,2000,id," ",false),event(42,100,7,2000,id,"OTHER",false));
        for(var command:invalid)assertThatThrownBy(()->core.apply(command)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->core.apply(null)).isInstanceOf(NullPointerException.class);
        assertThat(ports.calls).isEmpty();
    }
    @Test void failedAtomicApplyOrProjectionReadCannotMutateRoom() {
        ports.failure=new IllegalStateException("Redis down");
        assertThatThrownBy(()->core.apply(valid("BUSY",false))).isSameAs(ports.failure);
        assertThat(ports.calls).containsExactly("busy");
        ports.calls.clear(); ports.failOnRead=true;
        assertThatThrownBy(()->core.apply(valid("AVAILABLE",false))).isSameAs(ports.failure);
        assertThat(ports.calls).containsExactly("available","read");
    }
    @Test void dependenciesAreRequired() {
        assertThatThrownBy(()->new DefaultDeliveryRoomAssignmentUseCase(null,ports)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(()->new DefaultDeliveryRoomAssignmentUseCase(ports,null)).isInstanceOf(NullPointerException.class);
    }
    private static final class Ports implements DeliveryRoomAssignmentPort,DeliveryRoomIndexPort {
        Set<Long> active=Set.of(); RuntimeException failure; boolean failOnRead;
        final List<String> calls=new ArrayList<>();
        private void apply(String name,long shipper,long delivery,long time) {
            assertThat(shipper).isEqualTo(42); assertThat(delivery).isEqualTo(100); assertThat(time).isEqualTo(2000);
            calls.add(name); if(failure!=null&&!failOnRead)throw failure;
        }
        public void busy(long shipper,long delivery,long time,String id){apply("busy",shipper,delivery,time);}
        public void busyBatch(long shipper,long delivery,long time,String id){apply("busyBatch",shipper,delivery,time);}
        public void available(long shipper,long delivery,long time){apply("available",shipper,delivery,time);}
        public void availableBatch(long shipper,long delivery,long time){apply("availableBatch",shipper,delivery,time);}
        public void withinUpdate(long shipperId,Runnable operation){operation.run();}
        public Set<Long> activeDeliveries(long shipper){calls.add("read");if(failOnRead)throw failure;return active;}
        public void activate(long delivery,long shipper){calls.add("activate");assertThat(delivery).isEqualTo(100);assertThat(shipper).isEqualTo(42);}
        public void synchronize(long shipper,Set<Long> deliveries){calls.add("sync");assertThat(deliveries).isEqualTo(active);}
        public void subscribe(long delivery,long shipper,String session){throw new AssertionError("unexpected subscription");}
        public void end(long delivery,long shipper){calls.add("end");assertThat(delivery).isEqualTo(100);assertThat(shipper).isEqualTo(42);}
    }
}
