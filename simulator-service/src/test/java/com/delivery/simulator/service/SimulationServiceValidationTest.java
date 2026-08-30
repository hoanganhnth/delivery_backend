package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.delivery.simulator.entity.SimulationRun;
import com.delivery.simulator.repository.SimulationRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class SimulationServiceValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimulatorProperties properties = enabledProperties();
    private final SimulationService service = new SimulationService(objectMapper, properties, mock(GatewayClient.class));

    @AfterEach
    void stopRunner() {
        service.shutdown();
    }

    @Test
    void acceptsCodScenarioWithRealActorContracts() {
        var result = service.validate(validScenario());

        assertThat(result.get("valid")).isEqualTo(true);
        assertThat((java.util.List<?>) result.get("errors")).isEmpty();
    }

    @Test
    void managedActorPoolRequiresPrincipalReferencesInsteadOfScenarioTokens() {
        properties.setManagedActorPoolRequired(true);

        var invalid = service.validate(validScenario());

        assertThat(invalid.get("valid")).isEqualTo(false);
        assertThat((java.util.List<?>) invalid.get("errors"))
                .anyMatch(error -> error.toString().contains("principalId"));
    }

    @Test
    void managedActorPoolAcceptsCohortScopedPrincipalReferences() {
        properties.setManagedActorPoolRequired(true);
        ObjectNode scenario = validScenario();
        scenario.put("cohortId", UUID.randomUUID().toString());
        scenario.with("customer").remove("token");
        scenario.with("customer").put("principalId", 101L);
        scenario.with("restaurant").remove("ownerToken");
        scenario.with("restaurant").put("ownerPrincipalId", 102L);
        ObjectNode shipper = (ObjectNode) scenario.withArray("shippers").get(0);
        shipper.remove("token");
        shipper.put("principalId", 103L);

        var result = service.validate(scenario);

        assertThat(result.get("valid")).isEqualTo(true);
    }

    @Test
    void managedActorPoolInjectsOnlyRuntimeTokensForBoundPrincipalReferences() {
        properties.setManagedActorPoolRequired(true);
        ObjectNode scenario = managedActorScenario();
        SimulationActorPoolClient actors = mock(SimulationActorPoolClient.class);
        SimulationService managed = new SimulationService(objectMapper, properties, mock(GatewayClient.class),
                null, null, actors);
        SimulationRunState state = new SimulationRunState(objectMapper, scenario);
        UUID cohortId = UUID.fromString(scenario.path("cohortId").asText());
        when(actors.bind(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(java.util.UUID.fromString(state.getRunId())),
                org.mockito.ArgumentMatchers.eq(cohortId)))
                .thenAnswer(invocation -> {
                    long principalId = invocation.getArgument(0, Long.class);
                    return new SimulationActorPoolClient.BoundActor(principalId,
                            new com.delivery.identity.contracts.SimulationContext(
                                    com.delivery.identity.contracts.SimulationContext.ExecutionMode.SIMULATION,
                                    java.util.UUID.fromString(state.getRunId()), cohortId, principalId),
                            "runtime-token-" + principalId);
                });

        managed.resolveManagedActors(state);

        assertThat(state.getRawScenario().path("customer").path("token").asText()).isEqualTo("runtime-token-101");
        assertThat(state.getRawScenario().path("restaurant").path("ownerToken").asText()).isEqualTo("runtime-token-102");
        assertThat(state.getRawScenario().path("shippers").get(0).path("token").asText()).isEqualTo("runtime-token-103");
        verify(actors, times(3)).bind(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(java.util.UUID.fromString(state.getRunId())),
                org.mockito.ArgumentMatchers.eq(cohortId));
        managed.shutdown();
    }

    @Test
    void rejectsUnsupportedWalletMode() {
        ObjectNode scenario = validScenario();
        scenario.with("customer").put("paymentMethod", "INTERNAL_WALLET");

        var result = service.validate(scenario);

        assertThat(result.get("valid")).isEqualTo(false);
        assertThat((java.util.List<?>) result.get("errors"))
                .anyMatch(error -> error.toString().contains("COD"));
    }

    @Test
    void rejectsNonLocalGatewayTargetByDefault() {
        properties.setGatewayBaseUrl("https://production.example.test");

        assertThatThrownBy(() -> service.validate(validScenario()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ hoặc không an toàn");
    }

    @Test
    void rejectsProductionLikeTargetEvenWhenNonLocalTargetsAreAllowed() {
        properties.setAllowNonLocalTargets(true);
        properties.setGatewayBaseUrl("https://staging-gateway.example.test");

        assertThatThrownBy(() -> service.validate(validScenario()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ hoặc không an toàn");
    }

    @Test
    void snapshotsNeverExposeActorTokens() throws Exception {
        SimulationRunState state = new SimulationRunState(objectMapper, validScenario());

        String serialized = objectMapper.writeValueAsString(state.snapshot());

        assertThat(serialized).doesNotContain("customer-secret");
        assertThat(serialized).doesNotContain("owner-secret");
        assertThat(serialized).doesNotContain("shipper-secret");
    }

    @Test
    void durableScenarioNeverPersistsActorTokens() {
        SimulationRunState state = new SimulationRunState(objectMapper, validScenario());

        String persisted = state.persistableScenarioJson();

        assertThat(persisted).doesNotContain("customer-secret");
        assertThat(persisted).doesNotContain("owner-secret");
        assertThat(persisted).doesNotContain("shipper-secret");
        assertThat(persisted).contains("SIMULATED_ORDER", "shipper-1");
    }

    @Test
    void snapshotsExposeSortedConfiguredCandidateOracleWithoutTokens() {
        ObjectNode scenario = validScenario();
        scenario.with("restaurant").put("menuItemPrice", 100_000);
        ArrayNode shippers = scenario.withArray("shippers");
        ObjectNode farther = shippers.addObject();
        farther.put("id", "shipper-2");
        farther.put("token", "another-secret");
        farther.put("initialLat", 10.78);
        farther.put("initialLng", 106.71);
        farther.put("isOnline", true);
        farther.put("codBalance", 200_000);

        SimulationRunState state = new SimulationRunState(objectMapper, scenario);

        var candidates = (java.util.List<?>) state.snapshot().get("candidates");
        assertThat(candidates).hasSize(2);
        assertThat(objectMapper.valueToTree(candidates).toString()).contains("shipper-1", "shipper-2");
        assertThat(objectMapper.valueToTree(candidates).toString()).doesNotContain("another-secret");
    }

    @Test
    void assertionsWithoutIdsReceiveStableIdsForCompletion() {
        ObjectNode scenario = validScenario();
        scenario.withArray("assertions").addObject()
                .put("expectedTerminalState", "DELIVERED");

        SimulationRunState state = new SimulationRunState(objectMapper, scenario);

        var assertions = (java.util.List<?>) state.snapshot().get("assertions");
        assertThat(objectMapper.valueToTree(assertions).toString()).contains("assertion-1");
    }

    @Test
    void deliveredOrderWaitsForDeliveryProjectionToConverge() {
        assertThat(SimulationService.isTerminalProjectionConverged("DELIVERED", "PICKED_UP"))
                .isFalse();
        assertThat(SimulationService.isTerminalProjectionConverged("DELIVERED", "DELIVERED"))
                .isTrue();
    }

    @Test
    void terminalDeliveryAloneDoesNotMakeScenarioConverged() {
        assertThat(SimulationService.isTerminalProjectionConverged("PICKED_UP", "DELIVERED"))
                .isFalse();
    }

    @Test
    void cancellationWaitsForAnExistingDeliveryProjectionToConverge() {
        assertThat(SimulationService.isTerminalProjectionConverged("CANCELLED", "NONE"))
                .isTrue();
        assertThat(SimulationService.isTerminalProjectionConverged("CANCELLED", "ASSIGNED"))
                .isFalse();
        assertThat(SimulationService.isTerminalProjectionConverged("CANCELLED", "CANCELLED"))
                .isTrue();
        assertThat(SimulationService.isTerminalProjectionConverged("SHIPPER_NOT_FOUND", "NONE"))
                .isTrue();
    }

    @Test
    void treatsGatewayRateLimitAsTransientPollBackpressureOnly() {
        assertThat(SimulationService.isTransientRateLimit(
                new GatewayClient.GatewayException(429, "GET /api/deliveries/offers/current-batch",
                        "rate limited"))).isTrue();
        assertThat(SimulationService.isTransientRateLimit(
                new GatewayClient.GatewayException(401, "GET /api/deliveries/offers/current-batch",
                        "unauthorized"))).isFalse();
    }

    @Test
    void usesBoundedBackoffForRateLimitedLocationUpdates() {
        assertThat(SimulationService.locationRetryDelayMillis(0)).isEqualTo(1000L);
        assertThat(SimulationService.locationRetryDelayMillis(1)).isEqualTo(2000L);
        assertThat(SimulationService.locationRetryDelayMillis(8)).isEqualTo(30000L);
    }

    @Test
    void startupRecoveryAbortsPersistedRunsThatCannotSafelyResume() {
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SimulationRun unfinished = new SimulationRun(UUID.randomUUID(), "RUNNING", Instant.now(),
                Instant.now().plusSeconds(60), "{\"orderMode\":\"SIMULATED_ORDER\"}");
        when(runs.findByStatusIn(List.of("STARTING", "PROVISIONING", "RUNNING", "PAUSED")))
                .thenReturn(List.of(unfinished));
        SimulationService recovering = new SimulationService(objectMapper, properties,
                mock(GatewayClient.class), runs, null);

        recovering.reconcileOrphanedRuns();

        assertThat(unfinished.getStatus()).isEqualTo("ABORTED");
        verify(runs).saveAll(List.of(unfinished));
        recovering.shutdown();
    }

    @Test
    void startupRecoveryKeepsActorBindingsFencedBeforeMarkingRunAborted() throws Exception {
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SimulationActorPoolClient actors = mock(SimulationActorPoolClient.class);
        UUID runId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        ObjectNode scenario = managedActorScenario();
        scenario.put("cohortId", cohortId.toString());
        scenario.with("customer").put("simulationBindingVersion", 11L);
        scenario.with("restaurant").put("simulationBindingVersion", 12L);
        ((ObjectNode) scenario.withArray("shippers").get(0)).put("simulationBindingVersion", 13L);
        SimulationRun unfinished = new SimulationRun(runId, "RUNNING", Instant.now(),
                Instant.now().plusSeconds(60), objectMapper.writeValueAsString(scenario));
        when(runs.findByStatusIn(List.of("STARTING", "PROVISIONING", "RUNNING", "PAUSED")))
                .thenReturn(List.of(unfinished));
        SimulationService recovering = new SimulationService(objectMapper, properties,
                mock(GatewayClient.class), runs, null, actors);

        recovering.reconcileOrphanedRuns();

        org.mockito.Mockito.verifyNoInteractions(actors);
        assertThat(unfinished.getStatus()).isEqualTo("ABORTED");
        recovering.shutdown();
    }

    @Test
    void cleanupIsIdempotentForPersistedTerminalRunAfterMemoryStateIsGone() {
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        UUID runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(new SimulationRun(
                runId, "PASSED", Instant.now(), Instant.now(), "{}")));
        SimulationService durable = new SimulationService(objectMapper, properties,
                mock(GatewayClient.class), runs, null);

        Map<String, Object> result = durable.cleanup(runId.toString());

        assertThat(result).containsEntry("cleaned", true).containsEntry("idempotent", true);
        durable.shutdown();
    }

    @Test
    void rejectsMultiOrderBenchmarkWhenItContainsTriggersThatCannotBeRepeatedSafely() {
        ObjectNode scenario = validScenario();
        scenario.put("orderCount", 2);
        scenario.withArray("triggers").addObject()
                .put("enabled", true)
                .put("type", "CUSTOMER_CANCEL")
                .put("atStage", "PENDING")
                .put("delaySecondsAfterStage", 0);

        Map<String, Object> validation = service.validate(scenario);

        assertThat(validation.get("valid")).isEqualTo(false);
        assertThat((List<String>) validation.get("errors"))
                .anyMatch(error -> error.contains("orderCount > 1"));
    }

    private SimulatorProperties enabledProperties() {
        SimulatorProperties value = new SimulatorProperties();
        value.setEnabled(true);
        value.setManagedActorPoolRequired(false);
        value.setGatewayBaseUrl("http://localhost:8079");
        return value;
    }

    private ObjectNode validScenario() {
        ObjectNode scenario = objectMapper.createObjectNode();
        scenario.put("orderMode", "SIMULATED_ORDER");
        ObjectNode customer = scenario.putObject("customer");
        customer.put("token", "customer-secret");
        customer.put("paymentMethod", "COD");
        customer.put("name", "Test customer");
        customer.put("phone", "0900000000");
        customer.put("deliveryAddress", "Test address");
        customer.put("lat", 10.776);
        customer.put("lng", 106.7);
        customer.put("itemQuantity", 1);

        ObjectNode restaurant = scenario.putObject("restaurant");
        restaurant.put("id", 10);
        restaurant.put("menuItemId", 20);
        restaurant.put("ownerToken", "owner-secret");
        restaurant.put("autoConfirm", true);
        restaurant.put("menuItemPrice", 100_000);
        restaurant.put("lat", 10.775);
        restaurant.put("lng", 106.7);

        ArrayNode shippers = scenario.putArray("shippers");
        ObjectNode shipper = shippers.addObject();
        shipper.put("id", "shipper-1");
        shipper.put("token", "shipper-secret");
        shipper.put("isOnline", true);
        shipper.put("initialLat", 10.776);
        shipper.put("initialLng", 106.701);
        shipper.put("behavior", "AUTO_ACCEPT");

        scenario.putArray("triggers");
        scenario.putArray("assertions");
        return scenario;
    }

    private ObjectNode managedActorScenario() {
        ObjectNode scenario = validScenario();
        scenario.put("cohortId", UUID.randomUUID().toString());
        scenario.with("customer").remove("token");
        scenario.with("customer").put("principalId", 101L);
        scenario.with("restaurant").remove("ownerToken");
        scenario.with("restaurant").put("ownerPrincipalId", 102L);
        ObjectNode shipper = (ObjectNode) scenario.withArray("shippers").get(0);
        shipper.remove("token");
        shipper.put("principalId", 103L);
        return scenario;
    }
}
