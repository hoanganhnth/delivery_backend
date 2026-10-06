package com.delivery.simulator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_run_journal")
public class SimulationRunJournalEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private UUID runId;
    @Column(nullable = false) private Instant recordedAt;
    @Column(nullable = false, length = 64) private String source;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String payloadJson;
    protected SimulationRunJournalEntry() { }
    public SimulationRunJournalEntry(UUID runId, Instant recordedAt, String source, String title, String payloadJson) {
        this.runId = runId; this.recordedAt = recordedAt; this.source = source;
        this.title = title; this.payloadJson = payloadJson;
    }
    public UUID getRunId() { return runId; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getSource() { return source; }
    public String getTitle() { return title; }
    public String getPayloadJson() { return payloadJson; }
}
