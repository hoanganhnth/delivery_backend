package com.delivery.simulator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_ledger_entries")
public class SimulationLedgerEntry {
    @Id private UUID eventId;
    @Column(nullable = false) private UUID runId;
    @Column(nullable = false) private Long orderId;
    @Column(nullable = false) private Long deliveryId;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal totalPrice;
    @Column(nullable = false) private Instant recordedAt;
    protected SimulationLedgerEntry() {}
    public SimulationLedgerEntry(UUID eventId, UUID runId, Long orderId, Long deliveryId,
                                 BigDecimal totalPrice, Instant recordedAt) {
        this.eventId = eventId; this.runId = runId; this.orderId = orderId; this.deliveryId = deliveryId;
        this.totalPrice = totalPrice; this.recordedAt = recordedAt;
    }
    public UUID getEventId() { return eventId; }
}
