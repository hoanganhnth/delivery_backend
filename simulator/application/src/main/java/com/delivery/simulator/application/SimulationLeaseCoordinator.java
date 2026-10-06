package com.delivery.simulator.application;
import com.delivery.simulator.application.api.*;
import com.delivery.simulator.application.api.SimulationPorts.*;



import java.util.List;
import java.util.Map;

/** Coordinates memory run fences with durable lease operations; no Gateway access. */
public class SimulationLeaseCoordinator<S extends RunState,L extends Lease> {
    private final Leases<L> leaseService;
    private final Map<String, S> runs;
    private final Map<String, List<L>> runLeases;

    public SimulationLeaseCoordinator(Leases<L> leaseService,
                               Map<String, S> runs,
                               Map<String, List<L>> runLeases) {
        this.leaseService = leaseService;
        this.runs = runs;
        this.runLeases = runLeases;
    }

    public void release(String runId) {
        if (leaseService == null) return;
        List<L> leases = runLeases.get(runId);
        if (leases == null) return;
        // Preserve original fences until all calls complete, allowing outage retry.
        leases.forEach(lease -> leaseService.releaseOrQuarantine(lease.getLeaseId(), lease.getFencingToken()));
        runLeases.remove(runId, leases);
    }

    public void heartbeat() {
        if (leaseService == null) return;
        for (Map.Entry<String, List<L>> entry : runLeases.entrySet()) {
            S state = runs.get(entry.getKey());
            if (state == null || state.isTerminal()) continue;
            for (L lease : entry.getValue()) {
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
