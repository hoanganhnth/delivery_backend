package com.delivery.simulator.repository;

import com.delivery.simulator.entity.SimulationRun;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {
    List<SimulationRun> findByStatusIn(Collection<String> statuses);
    long countByStatusIn(Collection<String> statuses);
}
