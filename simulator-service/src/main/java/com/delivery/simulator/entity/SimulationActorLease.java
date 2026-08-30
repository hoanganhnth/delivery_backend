package com.delivery.simulator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_actor_leases")
public class SimulationActorLease {
    @Id private UUID leaseId;
    @Column(nullable = false) private UUID runId;
    @Column(nullable = false) private Long principalId;
    @Column(nullable = false) private Long fencingToken;
    @Column(nullable = false) private Instant leaseExpiresAt;
    @Column(nullable = false, length = 16) private String status;
    protected SimulationActorLease() {}
    public SimulationActorLease(UUID leaseId, UUID runId, Long principalId, Long fencingToken,
                                Instant leaseExpiresAt, String status) {
        this.leaseId = leaseId; this.runId = runId; this.principalId = principalId;
        this.fencingToken = fencingToken; this.leaseExpiresAt = leaseExpiresAt; this.status = status;
    }
    public UUID getLeaseId() { return leaseId; }
    public UUID getRunId() { return runId; }
    public Long getPrincipalId() { return principalId; }
    public Long getFencingToken() { return fencingToken; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void setLeaseExpiresAt(Instant value) { this.leaseExpiresAt = value; }
}
