package com.delivery.simulator.service;

import com.delivery.simulator.application.SimulationUseCases;
import com.delivery.simulator.application.api.SimulationPorts.*;
import com.delivery.simulator.config.SimulatorProperties;
import com.delivery.simulator.entity.*;
import com.delivery.simulator.repository.SimulationRunRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

/** Host composition, lifecycle and SSE adapter for the application use cases. */
@Service
public class SimulationService extends SimulationUseCases<SimulationRunState, SimulationRun, SimulationActorLease> {
    public SimulationService(ObjectMapper mapper, SimulatorProperties settings, GatewayClient gateway) {
        this(mapper,settings,gateway,null,null,null,null,new GatewayFaultInjection());
    }
    public SimulationService(ObjectMapper mapper, SimulatorProperties settings, GatewayClient gateway,
                             SimulationRunRepository runs, SimulationLeaseService leases) {
        this(mapper,settings,gateway,runs,leases,null,null,new GatewayFaultInjection());
    }
    public SimulationService(ObjectMapper mapper, SimulatorProperties settings, GatewayClient gateway,
                             SimulationRunRepository runs, SimulationLeaseService leases, SimulationActorPoolClient actors) {
        this(mapper,settings,gateway,runs,leases,actors,null,new GatewayFaultInjection());
    }
    @Autowired
    public SimulationService(ObjectMapper mapper, SimulatorProperties settings, GatewayClient gateway,
                             SimulationRunRepository runs, SimulationLeaseService leases, SimulationActorPoolClient actors,
                             SimulationRunJournalService journal, GatewayFaultInjection faults) {
        super(mapper,settings,gateway,runStore(runs),leases,actors,journal,faults,observations(mapper),
                scenario -> new SimulationRunState(mapper,scenario),systemTime(),
                Executors.newFixedThreadPool(settings.getMaxConcurrentRuns(), runnable -> {
                    Thread thread = new Thread(runnable,"simulator-runner"); thread.setDaemon(true); return thread;
                }));
    }
    static RunStore<SimulationRun> runStore(SimulationRunRepository repository) {
        if (repository == null) return null;
        return new RunStore<>() {
            public List<SimulationRun> findByStatusIn(List<String> statuses) { return repository.findByStatusIn(statuses); }
            public List<SimulationRun> findAll() { return repository.findAll(); }
            public Optional<SimulationRun> findById(UUID id) { return repository.findById(id); }
            public void save(SimulationRun run) { repository.save(run); }
            public void saveAll(List<SimulationRun> runs) { repository.saveAll(runs); }
            public SimulationRun create(UUID id,String status,Instant created,Instant expires,String json) {
                return new SimulationRun(id,status,created,expires,json);
            }
        };
    }
    static Time systemTime() {
        return new Time() {
            public Instant now() { return Instant.now(); }
            public long currentTimeMillis() { return System.currentTimeMillis(); }
            public long nanoTime() { return System.nanoTime(); }
            public void sleep(long millis) throws InterruptedException { Thread.sleep(millis); }
        };
    }
    static Observations observations(ObjectMapper mapper) {
        ShadowAlgorithmComparator comparator = new ShadowAlgorithmComparator(mapper);
        return new Observations() {
            public List<ObjectNode> compareAll(JsonNode trace,JsonNode scenario) { return comparator.compareAll(trace,scenario); }
            public Optional<DeliverySnapshot> delivery(JsonNode response,JsonNode scenario,String previous) {
                return SimulationDeliverySnapshot.parse(response,scenario,previous).map(value ->
                        new DeliverySnapshot(value.deliveryId(),value.status(),value.offeredShipper(),value.assignedShipper()));
            }
        };
    }
    public SseEmitter stream(String runId) {
        SimulationRunState state = streamState(runId);
        SseEmitter emitter = new SseEmitter(0L); state.addEmitter(emitter); return emitter;
    }
    @Override @PostConstruct public void reconcileOrphanedRuns() { super.reconcileOrphanedRuns(); }
    @Override @PreDestroy public void shutdown() { super.shutdown(); }
    @Override @Scheduled(fixedDelayString="${simulator.lease-heartbeat-delay-ms:5000}")
    public void heartbeatLeases() { super.heartbeatLeases(); }
    @Override @Scheduled(fixedDelayString="${simulator.run-expiry-check-delay-ms:10000}")
    public void abortExpiredRuns() { super.abortExpiredRuns(); }
}
