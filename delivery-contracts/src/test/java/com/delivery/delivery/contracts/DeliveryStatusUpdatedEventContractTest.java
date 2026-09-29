package com.delivery.delivery.contracts;

import com.delivery.identity.contracts.SimulationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryStatusUpdatedEventContractTest {
    @Test
    void serializesAllIdentityStatusAndSimulationFields() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var context = new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"), 4L);
        var event = new DeliveryStatusUpdatedEvent(id, "DELIVERY_STATUS_UPDATED",
                LocalDateTime.of(2026, 1, 2, 3, 4), 10L, 20L, 30L, 31L, 40L,
                "DELIVERED", "DELIVERING", "Ship A", context);
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(event);
        assertTrue(json.contains(id.toString())); assertTrue(json.contains("DELIVERED"));
        assertTrue(json.contains("previousStatus")); assertTrue(json.contains("userPrincipalId"));
        assertTrue(json.contains("simulationContext"));
        assertEquals(event, new ObjectMapper().registerModule(new JavaTimeModule())
                .readValue(json, DeliveryStatusUpdatedEvent.class));
    }
}
