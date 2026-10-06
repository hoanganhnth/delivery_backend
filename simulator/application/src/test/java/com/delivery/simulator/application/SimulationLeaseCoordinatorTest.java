package com.delivery.simulator.application;
import com.delivery.simulator.application.api.*;
import com.delivery.simulator.application.api.SimulationPorts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SimulationLeaseCoordinatorTest {
    @Test void heartbeatFencesOnLostOwnershipAndOutageAndReleaseRetainsRetryFence() {
        Leases<Lease> port=mock(Leases.class);RunState state=mock(RunState.class);Lease lease=mock(Lease.class);
        UUID id=UUID.randomUUID();when(lease.getLeaseId()).thenReturn(id);when(lease.getFencingToken()).thenReturn(5L);
        Map<String,RunState> runs=new HashMap<>();Map<String,List<Lease>> fences=new HashMap<>();
        var app=new SimulationLeaseCoordinator<>(port,runs,fences);app.release("absent");fences.put("run",List.of(lease));app.heartbeat();verifyNoInteractions(port);
        runs.put("run",state);when(state.isTerminal()).thenReturn(true);app.heartbeat();verifyNoInteractions(port);
        when(state.isTerminal()).thenReturn(false);when(port.renew(id,5)).thenReturn(true);app.heartbeat();verify(state,never()).abort();
        when(port.renew(id,5)).thenReturn(false);app.heartbeat();verify(state).abort();
        when(port.renew(id,5)).thenThrow(new IllegalStateException("db down"));assertThatThrownBy(app::heartbeat).hasMessage("db down");verify(state,times(2)).abort();
        when(port.releaseOrQuarantine(id,5)).thenThrow(new IllegalStateException("release down"));assertThatThrownBy(()->app.release("run")).hasMessage("release down");assertThat(fences).containsKey("run");
        doReturn(true).when(port).releaseOrQuarantine(id,5);app.release("run");assertThat(fences).isEmpty();
        var absent=new SimulationLeaseCoordinator<RunState,Lease>(null,runs,fences);absent.release("run");absent.heartbeat();
    }
}
