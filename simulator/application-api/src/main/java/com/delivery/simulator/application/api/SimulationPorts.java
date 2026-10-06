package com.delivery.simulator.application.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

/** Outbound boundaries. Implementations own transport, persistence and transaction scope. */
public final class SimulationPorts {
    private SimulationPorts() { }
    public interface Settings {
        boolean isEnabled(); boolean isManagedActorPoolRequired(); boolean isAllowNonLocalTargets();
        String getGatewayBaseUrl(); List<String> getAllowedGatewayHosts();
        int getPollIntervalMillis(); int getHumanOrderTimeoutSeconds(); int getRunTimeoutSeconds();
        int getMaxShippers(); int getMaxOrdersPerRun(); int getMovementTickSeconds();
    }
    public interface Gateway {
        JsonNode get(String path, String bearerToken, String correlationId);
        JsonNode post(String path, String bearerToken, JsonNode body, String correlationId);
        JsonNode postWithHeaders(String path, String bearerToken, JsonNode body, String correlationId, Map<String,String> headers);
        JsonNode put(String path, String bearerToken, JsonNode body, String correlationId);
    }
    public static class GatewayException extends RuntimeException {
        private final int status; private final String operation;
        public GatewayException(int status, String operation, String message) {
            super(message); this.status = status; this.operation = operation;
        }
        public int getStatus() { return status; }
        public String getOperation() { return operation; }
    }
    public interface Run {
        UUID getRunId(); String getStatus(); void setStatus(String status);
        Instant getCreatedAt(); Instant getExpiresAt(); String getScenarioJson();
    }
    public interface RunStore<R extends Run> {
        List<R> findByStatusIn(List<String> statuses); List<R> findAll(); Optional<R> findById(UUID id);
        void save(R run); void saveAll(List<R> runs);
        R create(UUID id, String status, Instant createdAt, Instant expiresAt, String scenarioJson);
    }
    public interface Lease { UUID getLeaseId(); Long getFencingToken(); }
    public interface Leases<L extends Lease> {
        L claim(UUID runId, Long principalId); boolean renew(UUID id, long fence);
        boolean releaseOrQuarantine(UUID id, long fence); boolean quarantine(UUID id, long fence);
        int quarantineRun(UUID runId); int releaseReconciledRun(UUID runId);
    }
    public interface Journal {
        void record(UUID runId, Map<String,Object> event); List<Map<String,Object>> entries(UUID runId);
    }
    public interface RecoveryJournal { List<String> payloads(UUID runId); }
    public interface Observations {
        List<ObjectNode> compareAll(JsonNode trace, JsonNode scenario);
        Optional<DeliverySnapshot> delivery(JsonNode response, JsonNode scenario, String previousStatus);
    }
    public record DeliverySnapshot(long deliveryId, String status, String offeredShipper, String assignedShipper) { }
    public interface Faults { void armOneTransientPollFailure(String correlationId); }
    public interface Time { Instant now(); long currentTimeMillis(); long nanoTime(); void sleep(long millis) throws InterruptedException; }
    public interface StateFactory<S extends RunState> { S create(JsonNode scenario); }
}
