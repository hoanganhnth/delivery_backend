package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class SimulationDeliverySnapshotTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "\"string\""})
    void nonObjectPayloadHasNoSnapshot(String payload) throws Exception {
        assertThat(SimulationDeliverySnapshot.parse(mapper.readTree(payload), mapper.createObjectNode(), "NONE")).isEmpty();
    }

    @Test
    void missingJavaResponseHasNoSnapshot() {
        assertThat(SimulationDeliverySnapshot.parse(null, mapper.createObjectNode(), "NONE")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "\"\""})
    void statusFallbackAndNullActorNormalizationRemainCompatible(String status) throws Exception {
        var result = SimulationDeliverySnapshot.parse(mapper.readTree(
                "{\"deliveryId\":2,\"status\":" + status + ",\"shipperId\":\"null\",\"offeredShipperId\":\"   \"}"),
                mapper.createObjectNode(), "ASSIGNED").orElseThrow();
        assertThat(result.deliveryId()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo(status.equals("\"\"") ? "" : "ASSIGNED");
        assertThat(result.assignedShipper()).isEmpty();
        assertThat(result.offeredShipper()).isEmpty();
    }

    @Test
    void userAliasMapsToActorAndUnknownActorIsPreservedWithoutMutatingInput() throws Exception {
        var scenario = mapper.readTree("{\"shippers\":[{\"id\":\"actor\",\"userId\":99}]}");
        var response = mapper.readTree("{\"id\":2,\"status\":\"ASSIGNED\",\"shipperId\":\"99\",\"offeredShipperId\":\"unknown\"}");
        var result = SimulationDeliverySnapshot.parse(response, scenario, "NONE").orElseThrow();
        assertThat(result.assignedShipper()).isEqualTo("actor");
        assertThat(result.offeredShipper()).isEqualTo("unknown");
        assertThat(response.path("shipperId").asText()).isEqualTo("99");
    }
}
