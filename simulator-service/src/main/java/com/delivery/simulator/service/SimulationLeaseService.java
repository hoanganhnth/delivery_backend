package com.delivery.simulator.service;

import com.delivery.simulator.entity.SimulationActorLease;
import com.delivery.simulator.repository.SimulationActorLeaseRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

/** Durable lease heartbeat/fence used by virtual shipper workers. */
@Service
public class SimulationLeaseService {
    private final SimulationActorLeaseRepository leases;
    private final long ttlSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public SimulationLeaseService(SimulationActorLeaseRepository leases,
                                  @org.springframework.beans.factory.annotation.Value("${simulator.lease-ttl-seconds:15}") long ttlSeconds) {
        this.leases = leases;
        this.ttlSeconds = Math.max(5L, ttlSeconds);
    }

    SimulationLeaseService(SimulationActorLeaseRepository leases, long ttlSeconds, boolean testOnly) {
        this.leases = leases;
        this.ttlSeconds = Math.max(5L, ttlSeconds);
    }

    @Transactional
    public SimulationActorLease claim(UUID runId, Long principalId) {
        if (runId == null || principalId == null || principalId <= 0) {
            throw new IllegalArgumentException("runId and positive principalId are required");
        }
        SimulationActorLease latest = leases.findTopByPrincipalIdOrderByFencingTokenDesc(principalId).orElse(null);
        if (latest != null && "ACTIVE".equals(latest.getStatus())
                && latest.getLeaseExpiresAt().isAfter(Instant.now())) {
            throw new IllegalStateException("Simulation actor already has an active lease");
        }
        long nextFence = latest == null ? 1L : latest.getFencingToken() + 1L;
        SimulationActorLease lease = new SimulationActorLease(UUID.randomUUID(), runId, principalId, nextFence,
                Instant.now().plusSeconds(ttlSeconds), "ACTIVE");
        leases.save(lease);
        return lease;
    }

    @Transactional
    public boolean renew(UUID leaseId, long fencingToken) {
        SimulationActorLease lease = leases.findById(leaseId).orElse(null);
        if (lease == null || !"ACTIVE".equals(lease.getStatus())
                || lease.getFencingToken() != fencingToken
                || lease.getLeaseExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        lease.setLeaseExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        leases.save(lease);
        return true;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${simulator.lease-reclaim-delay-ms:5000}")
    public int reclaimExpired() {
        int reclaimed = 0;
        for (SimulationActorLease lease : leases.findByStatusAndLeaseExpiresAtBefore("ACTIVE", Instant.now())) {
            lease.setStatus("EXPIRED");
            leases.save(lease);
            reclaimed++;
        }
        return reclaimed;
    }

    @Transactional
    public boolean release(UUID leaseId, long fencingToken) {
        SimulationActorLease lease = leases.findById(leaseId).orElse(null);
        if (lease == null || !"ACTIVE".equals(lease.getStatus())
                || lease.getFencingToken() != fencingToken) return false;
        lease.setStatus("RELEASED");
        lease.setLeaseExpiresAt(Instant.now());
        leases.save(lease);
        return true;
    }

    @Transactional
    public boolean releaseOrQuarantine(UUID leaseId, long fencingToken) {
        SimulationActorLease lease = leases.findById(leaseId).orElse(null);
        if (lease == null) return false;
        if (!"ACTIVE".equals(lease.getStatus()) || lease.getFencingToken() != fencingToken) {
            lease.setStatus("QUARANTINED");
            leases.save(lease);
            return false;
        }
        lease.setStatus("RELEASED");
        lease.setLeaseExpiresAt(Instant.now());
        leases.save(lease);
        return true;
    }

    @Transactional
    public boolean quarantine(UUID leaseId, long fencingToken) {
        SimulationActorLease lease = leases.findById(leaseId).orElse(null);
        if (lease == null || !"ACTIVE".equals(lease.getStatus()) || lease.getFencingToken() != fencingToken) {
            return false;
        }
        lease.setStatus("QUARANTINED");
        lease.setLeaseExpiresAt(Instant.now());
        leases.save(lease);
        return true;
    }

    @Transactional
    public int quarantineRun(UUID runId) {
        if (runId == null) return 0;
        int quarantined = 0;
        for (SimulationActorLease lease : leases.findByRunId(runId)) {
            if ("ACTIVE".equals(lease.getStatus())) {
                lease.setStatus("QUARANTINED");
                lease.setLeaseExpiresAt(Instant.now());
                leases.save(lease);
                quarantined++;
            }
        }
        return quarantined;
    }

    /** Marks a manually reconciled run's historical leases closed. */
    @Transactional
    public int releaseReconciledRun(UUID runId) {
        if (runId == null) return 0;
        int released = 0;
        for (SimulationActorLease lease : leases.findByRunId(runId)) {
            if ("QUARANTINED".equals(lease.getStatus()) || "EXPIRED".equals(lease.getStatus())) {
                lease.setStatus("RELEASED");
                lease.setLeaseExpiresAt(Instant.now());
                leases.save(lease);
                released++;
            }
        }
        return released;
    }
}
