package com.delivery.simulator.service;

import com.delivery.simulator.repository.SimulationActorLeaseRepository;
import com.delivery.simulator.repository.SimulationRunRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

/** Low-cardinality operational gauges for the isolated simulator control plane. */
@Component
public class SimulationOperationalMetrics {
    private static final List<String> ACTIVE_RUN_STATUSES = List.of(
            "STARTING", "PROVISIONING", "RUNNING", "PAUSED");

    public SimulationOperationalMetrics(MeterRegistry registry,
                                        SimulationRunRepository runs,
                                        SimulationActorLeaseRepository leases) {
        Gauge.builder("delivery_simulator_runs_active", runs,
                        repository -> repository.countByStatusIn(ACTIVE_RUN_STATUSES))
                .description("Simulator runs that still own worker lifecycle")
                .register(registry);
        leaseGauge(registry, leases, "ACTIVE", "active");
        Gauge.builder("delivery_simulator_actor_leases", leases,
                        SimulationActorLeaseRepository::countPendingReconciliation)
                .tag("state", "quarantined")
                .description("Quarantined simulator actor leases requiring reconciliation")
                .register(registry);
    }

    private void leaseGauge(MeterRegistry registry, SimulationActorLeaseRepository leases,
                            String databaseStatus, String metricState) {
        Gauge.builder("delivery_simulator_actor_leases", leases,
                        repository -> repository.countByStatus(databaseStatus))
                .tag("state", metricState)
                .description("Simulator actor leases by operational state")
                .register(registry);
    }
}
