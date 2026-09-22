package com.delivery.search.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntitySyncEventContractTest {
    @Test
    void serializesAndDeserializesExistingWireFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        EntitySyncEvent source = new EntitySyncEvent();
        source.setEventId(UUID.randomUUID());
        source.setOccurredAt(LocalDateTime.of(2026, 9, 22, 12, 30));
        source.setEntityType("RESTAURANT");
        source.setAction("UPDATE");
        source.setEntityId("7");
        source.setPayload(Map.of("name", "R"));

        EntitySyncEvent restored = mapper.readValue(mapper.writeValueAsBytes(source), EntitySyncEvent.class);

        assertThat(restored.getEventId()).isEqualTo(source.getEventId());
        assertThat(restored.getOccurredAt()).isEqualTo(source.getOccurredAt());
        assertThat(restored.getPayload()).containsEntry("name", "R");
    }
}
