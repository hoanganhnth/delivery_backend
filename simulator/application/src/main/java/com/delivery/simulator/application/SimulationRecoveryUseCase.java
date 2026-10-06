package com.delivery.simulator.application;
import com.delivery.simulator.application.api.*;
import com.delivery.simulator.application.api.SimulationPorts.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Operator recovery for runs marked ABORTED after a simulator restart.
 * Credentials are re-issued only for the original fenced run and remain
 * memory-only. Auth bindings are released only after every persisted delivery
 * can be read through the customer identity and is terminal.
 */
public class SimulationRecoveryUseCase<R extends Run> {
    private static final Set<String> TERMINAL = Set.of(
            "DELIVERED", "CANCELLED", "REJECTED", "SHIPPER_NOT_FOUND");

    private final ObjectMapper mapper;
    private final RunStore<R> runs;
    private final RecoveryJournal journal;
    private final Gateway gateway;
    private final SimulationActorPoolClient actors;
    private final Leases<?> leases;
    private final SimulationDeliveryRecoveryClient deliveryRecovery;

    public SimulationRecoveryUseCase(ObjectMapper mapper,
                                     RunStore<R> runs,
                                     RecoveryJournal journal,
                                     Gateway gateway,
                                     SimulationActorPoolClient actors) {
        this(mapper, runs, journal, gateway, actors, null);
    }

    public SimulationRecoveryUseCase(ObjectMapper mapper,
                                     RunStore<R> runs,
                                     RecoveryJournal journal,
                                     Gateway gateway,
                                     SimulationActorPoolClient actors,
                                     Leases<?> leases) {
        this(mapper, runs, journal, gateway, actors, leases, null);
    }

    public SimulationRecoveryUseCase(ObjectMapper mapper,
                                     RunStore<R> runs,
                                     RecoveryJournal journal,
                                     Gateway gateway,
                                     SimulationActorPoolClient actors,
                                     Leases<?> leases,
                                     SimulationDeliveryRecoveryClient deliveryRecovery) {
        this.mapper = mapper;
        this.runs = runs;
        this.journal = journal;
        this.gateway = gateway;
        this.actors = actors;
        this.leases = leases;
        this.deliveryRecovery = deliveryRecovery;
    }

    public RecoveryResult reconcile(UUID runId) {
        if (runId == null) throw new IllegalArgumentException("runId is required");
        R run = runs.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy simulator run: " + runId));
        if (!Set.of("ABORTED", "FAILED").contains(run.getStatus())) {
            throw new IllegalStateException("Chỉ được reconcile run ABORTED hoặc FAILED");
        }
        try {
            JsonNode scenario = mapper.readTree(run.getScenarioJson());
            UUID cohortId = UUID.fromString(scenario.path("cohortId").asText());
            Set<Long> deliveryIds = extractDeliveryIds(journal.payloads(runId));
            Map<Long, String> recoveredStatuses = new LinkedHashMap<>();
            if (deliveryIds.isEmpty() && deliveryRecovery != null) {
                for (SimulationDeliveryRecoveryClient.DeliveryStatus delivery : deliveryRecovery.findByRunId(runId)) {
                    if (delivery.deliveryId() != null && delivery.deliveryId() > 0) {
                        deliveryIds.add(delivery.deliveryId());
                        recoveredStatuses.put(delivery.deliveryId(), delivery.status());
                    }
                }
            }
            // A run without a durable delivery identity cannot be proven safe;
            // importantly, do not bind any actors just to discover that fact.
            if (deliveryIds.isEmpty()) {
                return new RecoveryResult(runId, false, List.of(), Map.of(), 0);
            }
            List<SimulationActorPoolClient.BoundActor> bound = bindActors(scenario, runId, cohortId);
            SimulationActorPoolClient.BoundActor customer = bound.stream()
                    .filter(actor -> actor.principalId() == scenario.path("customer").path("principalId").asLong(-1))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Thiếu customer actor trong scenario"));
            Map<Long, String> statuses = new LinkedHashMap<>(recoveredStatuses);
            for (Long deliveryId : deliveryIds) {
                if (statuses.containsKey(deliveryId)) continue;
                JsonNode response = gateway.get("/api/deliveries/" + deliveryId,
                        customer.accessToken(), "recovery-" + runId);
                JsonNode data = response.has("data") ? response.path("data") : response;
                String status = data.path("status").asText("UNKNOWN");
                statuses.put(deliveryId, status);
            }
            if (!statuses.values().stream().allMatch(TERMINAL::contains)) {
                return new RecoveryResult(runId, false, new ArrayList<>(deliveryIds), statuses, 0);
            }
            for (SimulationActorPoolClient.BoundActor actor : bound) {
                actors.unbind(actor.principalId(), runId, actor.context().bindingVersion());
            }
            if (leases != null) leases.releaseReconciledRun(runId);
            return new RecoveryResult(runId, true, new ArrayList<>(deliveryIds), statuses, bound.size());
        } catch (Exception error) {
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Không thể reconcile simulator run", error);
        }
    }

    private List<SimulationActorPoolClient.BoundActor> bindActors(JsonNode scenario, UUID runId, UUID cohortId) {
        List<SimulationActorPoolClient.BoundActor> result = new ArrayList<>();
        try {
            bind(result, scenario.path("customer").path("principalId").asLong(-1), runId, cohortId);
            bind(result, scenario.path("restaurant").path("ownerPrincipalId").asLong(-1), runId, cohortId);
            for (JsonNode shipper : scenario.path("shippers")) {
                bind(result, shipper.path("principalId").asLong(-1), runId, cohortId);
            }
            return result;
        } catch (RuntimeException failure) {
            for (SimulationActorPoolClient.BoundActor actor : result) {
                try { actors.unbind(actor.principalId(), runId, actor.context().bindingVersion()); }
                catch (RuntimeException ignored) { }
            }
            throw failure;
        }
    }

    private void bind(List<SimulationActorPoolClient.BoundActor> target, long principalId,
                      UUID runId, UUID cohortId) {
        if (principalId <= 0) throw new IllegalArgumentException("Scenario actor principalId không hợp lệ");
        target.add(actors.bind(principalId, runId, cohortId));
    }

    private Set<Long> extractDeliveryIds(List<String> entries) {
        Set<Long> result = new LinkedHashSet<>();
        for (String entry : entries) {
            try { collectIds(mapper.readTree(entry), result); }
            catch (Exception ignored) { /* old journal rows may contain plain text */ }
        }
        return result;
    }

    private void collectIds(JsonNode node, Set<Long> result) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if ("deliveryId".equals(field.getKey()) && field.getValue().isIntegralNumber()
                        && field.getValue().canConvertToLong() && field.getValue().asLong() > 0) {
                    result.add(field.getValue().asLong());
                }
                collectIds(field.getValue(), result);
            });
        } else if (node.isArray()) node.forEach(child -> collectIds(child, result));
    }

    public record RecoveryResult(UUID runId, boolean reconciled, List<Long> deliveryIds,
                                 Map<Long, String> deliveryStatuses, int releasedActors) { }
}
