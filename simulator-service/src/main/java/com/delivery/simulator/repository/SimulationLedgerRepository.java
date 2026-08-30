package com.delivery.simulator.repository;

import com.delivery.simulator.entity.SimulationLedgerEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationLedgerRepository extends JpaRepository<SimulationLedgerEntry, UUID> {}
