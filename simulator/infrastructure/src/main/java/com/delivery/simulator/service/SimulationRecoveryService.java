package com.delivery.simulator.service;
import com.delivery.simulator.application.SimulationRecoveryUseCase;
import com.delivery.simulator.entity.SimulationRun;
import com.delivery.simulator.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
/** Recovery adapter composition; persistence and transaction boundaries stay in the host. */
@Service
public class SimulationRecoveryService extends SimulationRecoveryUseCase<SimulationRun> {
    public SimulationRecoveryService(ObjectMapper mapper,SimulationRunRepository runs,SimulationRunJournalRepository journal,
                                     GatewayClient gateway,SimulationActorPoolClient actors) {
        this(mapper,runs,journal,gateway,actors,null,null);
    }
    public SimulationRecoveryService(ObjectMapper mapper,SimulationRunRepository runs,SimulationRunJournalRepository journal,
                                     GatewayClient gateway,SimulationActorPoolClient actors,SimulationLeaseService leases) {
        this(mapper,runs,journal,gateway,actors,leases,null);
    }
    @Autowired
    public SimulationRecoveryService(ObjectMapper mapper,SimulationRunRepository runs,SimulationRunJournalRepository journal,
                                     GatewayClient gateway,SimulationActorPoolClient actors,SimulationLeaseService leases,
                                     SimulationDeliveryRecoveryClient recovery) {
        super(mapper,SimulationService.runStore(runs),runId -> journal.findByRunIdOrderByIdAsc(runId).stream()
                .map(entry -> entry.getPayloadJson()).toList(),gateway,actors,leases,recovery);
    }
}
