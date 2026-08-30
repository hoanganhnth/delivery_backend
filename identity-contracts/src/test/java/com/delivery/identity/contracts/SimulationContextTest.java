package com.delivery.identity.contracts;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulationContextTest {

    @Test
    void acceptsRealContextWithoutRunIdentity() {
        assertDoesNotThrow(() -> SimulationContext.real().requireValid());
    }

    @Test
    void rejectsSimulationContextWithoutRunIdentity() {
        var invalid = new SimulationContext(SimulationContext.ExecutionMode.SIMULATION, null,
                UUID.randomUUID(), 1L);

        assertThrows(IllegalArgumentException.class, invalid::requireValid);
    }

    @Test
    void rejectsRealContextThatCarriesSimulationIdentity() {
        var invalid = new SimulationContext(SimulationContext.ExecutionMode.REAL, UUID.randomUUID(),
                UUID.randomUUID(), 1L);

        assertThrows(IllegalArgumentException.class, invalid::requireValid);
    }

    @Test
    void jacksonRoundTripDoesNotExposeDerivedSimulationFlag() throws Exception {
        var context = new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,
                UUID.randomUUID(), UUID.randomUUID(), 3L);
        var objectMapper = new ObjectMapper();

        String json = objectMapper.writeValueAsString(context);

        assertEquals(context, objectMapper.readValue(json, SimulationContext.class));
    }
}
