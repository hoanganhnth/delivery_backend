package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
    void cancellationCanFinishWithoutASeparateDeliveryProjection() {
        assertThat(SimulationService.isTerminalProjectionConverged("CANCELLED", "NONE"))
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

    private SimulatorProperties enabledProperties() {
        SimulatorProperties value = new SimulatorProperties();
        value.setEnabled(true);
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
}
