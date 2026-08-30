package com.delivery.simulator.repository;

import com.delivery.simulator.entity.SimulationRunJournalEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRunJournalRepository extends JpaRepository<SimulationRunJournalEntry, Long> {
    java.util.List<SimulationRunJournalEntry> findByRunIdOrderByIdAsc(UUID runId);
}
