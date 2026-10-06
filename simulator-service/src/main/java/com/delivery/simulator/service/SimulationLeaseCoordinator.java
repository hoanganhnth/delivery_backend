package com.delivery.simulator.service;
import com.delivery.simulator.entity.SimulationActorLease;
import java.util.*;
/** Compatibility composition for host lease coordinator tests. */
final class SimulationLeaseCoordinator extends com.delivery.simulator.application.SimulationLeaseCoordinator<SimulationRunState,SimulationActorLease> {
    SimulationLeaseCoordinator(SimulationLeaseService leases,Map<String,SimulationRunState> runs,
                               Map<String,List<SimulationActorLease>> fences) { super(leases,runs,fences); }
}
