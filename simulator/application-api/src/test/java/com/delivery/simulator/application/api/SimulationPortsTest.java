package com.delivery.simulator.application.api;
import com.delivery.identity.contracts.SimulationContext;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class SimulationPortsTest {
    @Test void gatewayFailureRetainsTransportFields() {
        var error=new SimulationPorts.GatewayException(429,"GET /poll","limited");
        assertThat(error.getStatus()).isEqualTo(429); assertThat(error.getOperation()).isEqualTo("GET /poll");
        assertThat(error).hasMessage("limited");
        var snapshot=new SimulationPorts.DeliverySnapshot(1,"DELIVERED","offer","assigned");
        assertThat(snapshot.deliveryId()).isEqualTo(1); assertThat(snapshot.status()).isEqualTo("DELIVERED");
        assertThat(snapshot.offeredShipper()).isEqualTo("offer"); assertThat(snapshot.assignedShipper()).isEqualTo("assigned");
        var status=new SimulationDeliveryRecoveryClient.DeliveryStatus(1L,2L,"CANCELLED");
        assertThat(status.deliveryId()).isEqualTo(1); assertThat(status.orderId()).isEqualTo(2); assertThat(status.status()).isEqualTo("CANCELLED");
    }
    @Test void boundActorValidatesAllFieldsAndContext() {
        var context=new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,UUID.randomUUID(),UUID.randomUUID(),1L);
        for(Long id:new Long[]{null,-1L,0L,1L}) for(SimulationContext c:new SimulationContext[]{null,context}) for(String token:new String[]{null,""," ","token"}) {
            if(id!=null&&id>0&&c!=null&&token!=null&&!token.isBlank()) {
                var actor=new SimulationActorPoolClient.BoundActor(id,c,token);
                assertThat(actor.principalId()).isEqualTo(id); assertThat(actor.context()).isSameAs(c); assertThat(actor.accessToken()).isEqualTo(token);
            } else assertThatThrownBy(()->new SimulationActorPoolClient.BoundActor(id,c,token)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->new SimulationActorPoolClient.BoundActor(1L,new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,null,null,0L),"token")).isInstanceOf(IllegalArgumentException.class);
    }
}
