package com.delivery.search_service.consumer;

import com.delivery.search_service.dto.EntitySyncEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SearchTombstoneMetadataTest {
    @Test
    void rejectsInvalidSoftDeleteMetadataBeforeCheckpointMutation() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter writer = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, writer, new ObjectMapper());
        EntitySyncEvent event = EntitySyncEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(LocalDateTime.of(2026, 9, 22, 12, 30))
                .deletedAt(LocalDateTime.of(2026, 9, 22, 12, 31))
                .aggregateVersion(0L)
                .entityType("RESTAURANT")
                .entityId("7")
                .action("DELETE")
                .deletionReason("merchant_retired")
                .build();

        assertThrows(IllegalArgumentException.class, () -> consumer.consumeEntitySyncEvent(event));
        verifyNoInteractions(checkpoints, writer);
    }
}
