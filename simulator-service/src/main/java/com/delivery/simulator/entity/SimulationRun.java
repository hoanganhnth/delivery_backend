package com.delivery.simulator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_runs")
public class SimulationRun {
    @Id
    private UUID runId;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String scenarioJson;

    protected SimulationRun() {}
    public SimulationRun(UUID runId, String status, Instant createdAt, Instant expiresAt, String scenarioJson) {
        this.runId = runId; this.status = status; this.createdAt = createdAt;
        this.expiresAt = expiresAt; this.scenarioJson = scenarioJson;
    }
    public UUID getRunId() { return runId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getScenarioJson() { return scenarioJson; }
}
