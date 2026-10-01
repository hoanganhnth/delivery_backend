package com.delivery.simulator.service;

import com.delivery.simulator.entity.SimulationActorLease;

import java.util.List;
import java.util.Map;

/** Coordinates memory run fences with durable lease operations; no Gateway access. */
final class SimulationLeaseCoordinator {
    private final SimulationLeaseService leaseService;
    private final Map<String, SimulationRunState> runs;
    private final Map<String, List<SimulationActorLease>> runLeases;

    SimulationLeaseCoordinator(SimulationLeaseService leaseService,
                               Map<String, SimulationRunState> runs,
                               Map<String, List<SimulationActorLease>> runLeases) {
        this.leaseService = leaseService;
        this.runs = runs;
        this.runLeases = runLeases;
    }

    void release(String runId) {
        if (leaseService == null) return;
        List<SimulationActorLease> leases = runLeases.get(runId);
        if (leases == null) return;
        // Preserve original fences until all calls complete, allowing outage retry.
        leases.forEach(lease -> leaseService.releaseOrQuarantine(lease.getLeaseId(), lease.getFencingToken()));
        runLeases.remove(runId, leases);
    }

    void heartbeat() {
        if (leaseService == null) return;
        for (Map.Entry<String, List<SimulationActorLease>> entry : runLeases.entrySet()) {
            SimulationRunState state = runs.get(entry.getKey());
            if (state == null || state.isTerminal()) continue;
            for (SimulationActorLease lease : entry.getValue()) {
                boolean renewed;
                try {
                    renewed = leaseService.renew(lease.getLeaseId(), lease.getFencingToken());
                } catch (RuntimeException failure) {
                    // Fence before surfacing an outage: ownership is unproven.
                    state.abort();
                    state.addEvent("LEASE", "Không thể gia hạn lease", "Run bị dừng vì không xác nhận được quyền sở hữu actor", "ERROR");
                    throw failure;
                }
                if (!renewed) {
                    state.abort();
                    state.addEvent("LEASE", "Lease shipper hết hạn", "Run bị dừng để tránh worker stale gửi action", "ERROR");
                    break;
                }
            }
        }
    }
}
