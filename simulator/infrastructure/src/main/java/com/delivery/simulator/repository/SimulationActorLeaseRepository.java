package com.delivery.simulator.repository;

import com.delivery.simulator.entity.SimulationActorLease;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;

public interface SimulationActorLeaseRepository extends JpaRepository<SimulationActorLease, UUID> {
    Optional<SimulationActorLease> findTopByPrincipalIdOrderByFencingTokenDesc(Long principalId);
    List<SimulationActorLease> findByStatusAndLeaseExpiresAtBefore(String status, Instant cutoff);
    List<SimulationActorLease> findByRunId(UUID runId);
    long countByStatus(String status);
    @Query(value = "SELECT COUNT(*) FROM simulation_actor_leases l "
            + "JOIN simulation_runs r ON r.run_id = l.run_id "
            + "WHERE l.status = 'QUARANTINED' AND r.status IN ('ABORTED', 'FAILED')",
            nativeQuery = true)
    long countPendingReconciliation();
}
