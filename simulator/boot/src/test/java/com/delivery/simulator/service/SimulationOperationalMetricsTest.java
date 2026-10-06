package com.delivery.simulator.service;

import com.delivery.simulator.repository.SimulationActorLeaseRepository;
import com.delivery.simulator.repository.SimulationRunRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationOperationalMetricsTest {
    @Test
    void exposesActiveRunsAndLeaseStatesWithoutRunOrActorLabels() {
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SimulationActorLeaseRepository leases = mock(SimulationActorLeaseRepository.class);
        when(runs.countByStatusIn(List.of("STARTING", "PROVISIONING", "RUNNING", "PAUSED"))).thenReturn(3L);
        when(leases.countByStatus("ACTIVE")).thenReturn(7L);
        when(leases.countPendingReconciliation()).thenReturn(2L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new SimulationOperationalMetrics(registry, runs, leases);

        assertThat(registry.get("delivery_simulator_runs_active").gauge().value()).isEqualTo(3d);
        assertThat(registry.get("delivery_simulator_actor_leases").tag("state", "active").gauge().value()).isEqualTo(7d);
        assertThat(registry.get("delivery_simulator_actor_leases").tag("state", "quarantined").gauge().value()).isEqualTo(2d);
    }
}
