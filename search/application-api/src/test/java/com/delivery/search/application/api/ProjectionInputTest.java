package com.delivery.search.application.api;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionInputTest {
    @Test void metadataPreservesEveryAdmissionValueAndPayloadPresence() {
        UUID id = UUID.randomUUID();
        LocalDateTime time = LocalDateTime.of(2026, 9, 30, 10, 0);
        for (Map<String, Object> payload : java.util.Arrays.asList(null, Map.<String, Object>of())) {
            ProjectionInput input = new ProjectionInput(id, time, "dish", "DELETE", "1", payload, 7L, time, "reason");
            assertEquals(id, input.metadata().eventId());
            assertEquals(time, input.metadata().occurredAt());
            assertEquals("dish", input.metadata().entityType());
            assertEquals("DELETE", input.metadata().action());
            assertEquals("1", input.metadata().entityId());
            assertEquals(payload != null, input.metadata().payloadPresent());
            assertEquals(7L, input.metadata().aggregateVersion());
            assertEquals(time, input.metadata().deletedAt());
            assertSame(payload, input.payload());
            assertEquals("reason", input.deletionReason());
        }
        assertEquals(3, ProjectionPorts.Claim.values().length);
        assertEquals(ProjectionPorts.Claim.EXACT_REPLAY, ProjectionPorts.Claim.valueOf("EXACT_REPLAY"));
        assertEquals(" q ", new SearchQuery(" q ").text());
        assertEquals("unavailable", new SearchUnavailableException("unavailable").getMessage());
        RuntimeException cause = new RuntimeException();
        assertSame(cause, new SearchUnavailableException("failed", cause).getCause());
    }
}
