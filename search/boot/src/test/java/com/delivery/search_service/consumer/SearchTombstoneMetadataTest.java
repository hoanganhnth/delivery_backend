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
    @Test
    void futureDeletedAtIsRejectedEvenWithValidAggregateVersion() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter writer = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, writer, new ObjectMapper());
        EntitySyncEvent event = EntitySyncEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(LocalDateTime.of(2026, 9, 22, 12, 30))
                .deletedAt(LocalDateTime.of(2026, 9, 22, 12, 30).plusNanos(1))
                .aggregateVersion(1L).entityType("dish").entityId("7").action("delete").build();

        var exception = assertThrows(IllegalArgumentException.class, () -> consumer.consumeEntitySyncEvent(event));
        org.junit.jupiter.api.Assertions.assertEquals("deletedAt cannot be after occurredAt", exception.getMessage());
        verifyNoInteractions(checkpoints, writer);
    }

    @Test
    void nullEnvelopeAndMissingPayloadFailBeforeAnyAdapterMutation() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter writer = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, writer, new ObjectMapper());
        var absent = assertThrows(IllegalArgumentException.class, () -> consumer.consumeEntitySyncEvent(null));
        org.junit.jupiter.api.Assertions.assertEquals(
                "stable eventId, occurredAt, entityType, action and entityId are required", absent.getMessage());
        EntitySyncEvent event = EntitySyncEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(LocalDateTime.of(2026, 9, 22, 12, 30))
                .entityType("DISH").entityId("7").action("UPDATE").build();
        var payload = assertThrows(IllegalArgumentException.class, () -> consumer.consumeEntitySyncEvent(event));
        org.junit.jupiter.api.Assertions.assertEquals("payload is required for create/update", payload.getMessage());
        verifyNoInteractions(checkpoints, writer);
    }

}
