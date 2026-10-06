package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationScenarioRedactionTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"accessToken\":\"credential-marker\",\"seed\":42}",
            "{\"metadata\":{\"ownerToken\":\"credential-marker\",\"seed\":42}}",
            "{\"metadata\":[{\"token\":\"credential-marker\",\"seed\":42}]}"
    })
    void durableAndPublicScenarioRedactCredentialsRecursivelyWithoutMutatingRuntime(String raw) throws Exception {
        var mapper = new ObjectMapper();
        var scenario = mapper.readTree(raw);
        var state = new SimulationRunState(mapper, scenario);
        assertThat(state.persistableScenarioJson()).doesNotContain("credential-marker").contains("42");
        assertThat(mapper.writeValueAsString(state.snapshot().get("scenario")))
                .doesNotContain("credential-marker").contains("42");
        assertThat(state.getRawScenario().toString()).contains("credential-marker");
        assertThat(scenario.toString()).contains("credential-marker");
    }
}
