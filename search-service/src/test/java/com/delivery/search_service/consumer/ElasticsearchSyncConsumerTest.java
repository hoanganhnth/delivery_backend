package com.delivery.search_service.consumer;

import com.delivery.search_service.dto.EntitySyncEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.UUID;

class ElasticsearchSyncConsumerTest {

    @Test
    void invalidEventFailsSoKafkaCanRetryAndDeadLetterIt() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter projections = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        EntitySyncEvent event = EntitySyncEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(LocalDateTime.now())
                .entityType("RESTAURANT")
                .entityId("1")
                .action("UPSERT_UNKNOWN")
                .payload(java.util.Map.of("name", "R"))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> consumer.consumeEntitySyncEvent(event));

        verifyNoInteractions(checkpoints, projections);
    }

    @Test
    void staleReplayCannotOverwriteNewerSearchDocument() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter projections = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        LocalDateTime oldTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        EntitySyncEvent event = EntitySyncEvent.builder()
                .eventId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .occurredAt(oldTime)
                .entityType("RESTAURANT").entityId("1").action("UPDATE")
                .payload(java.util.Map.of("name", "Old"))
                .build();
        when(checkpoints.claim(eq(event), anyString()))
                .thenReturn(EntitySyncCheckpointStore.ClaimResult.STALE);

        consumer.consumeEntitySyncEvent(event);

        verify(checkpoints).claim(eq(event), anyString());
        verifyNoInteractions(projections);
    }

    @Test
    void exactReplayCanReapplyDocumentAfterCheckpointBeforeMutationCrash() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter projections = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        EntitySyncEvent event = event(java.util.Map.of("name", "Canonical"));

        when(checkpoints.claim(eq(event), anyString()))
                .thenReturn(EntitySyncCheckpointStore.ClaimResult.APPLY,
                        EntitySyncCheckpointStore.ClaimResult.EXACT_REPLAY);

        consumer.consumeEntitySyncEvent(event);
        consumer.consumeEntitySyncEvent(event);

        verify(projections, times(2)).apply(event);
    }

    @Test
    void sameEventIdWithChangedPayloadIsRejected() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter projections = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        EntitySyncEvent original = event(java.util.Map.of("name", "Canonical"));
        EntitySyncEvent contradiction = event(java.util.Map.of("name", "Tampered"));

        when(checkpoints.claim(eq(original), anyString()))
                .thenReturn(EntitySyncCheckpointStore.ClaimResult.APPLY);
        when(checkpoints.claim(eq(contradiction), anyString()))
                .thenThrow(new IllegalArgumentException("contradictory payload"));
        consumer.consumeEntitySyncEvent(original);

        assertThrows(IllegalArgumentException.class,
                () -> consumer.consumeEntitySyncEvent(contradiction));
        verify(projections).apply(original);
        verify(projections, never()).apply(contradiction);
    }

    @Test
    void checkpointFailurePropagatesSoKafkaCanRetryRatherThanMutateProjection() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter projections = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        EntitySyncEvent event = event(java.util.Map.of("name", "Retry me"));
        when(checkpoints.claim(eq(event), anyString()))
                .thenThrow(new IllegalStateException("Elasticsearch unavailable"));

        assertThrows(IllegalStateException.class, () -> consumer.consumeEntitySyncEvent(event));

        verifyNoInteractions(projections);
    }

    @Test
    void removedShipperSearchEventIsRejectedBeforeCheckpointOrDocumentMutation() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter projections = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        EntitySyncEvent event = EntitySyncEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(LocalDateTime.now())
                .entityType("SHIPPER")
                .entityId("7")
                .action("UPDATE")
                .payload(java.util.Map.of("name", "Hidden shipper"))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> consumer.consumeEntitySyncEvent(event));

        verifyNoInteractions(checkpoints, projections);
    }

    @Test
    void missingRepositoryFailsAfterCheckpointSoKafkaCanRetryProjection() {
        EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
        SearchProjectionWriter projections = mock(SearchProjectionWriter.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        EntitySyncEvent event = event(java.util.Map.of("name", "Retry me"));
        when(checkpoints.claim(eq(event), anyString()))
                .thenReturn(EntitySyncCheckpointStore.ClaimResult.APPLY);
        doThrow(new IllegalStateException("repository unavailable")).when(projections).apply(event);

        assertThrows(IllegalStateException.class,
                () -> consumer.consumeEntitySyncEvent(event));

        verify(projections).apply(event);
    }

    private static EntitySyncEvent event(java.util.Map<String, Object> payload) {
        return EntitySyncEvent.builder()
                .eventId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .occurredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .entityType("RESTAURANT")
                .entityId("1")
                .action("UPDATE")
                .payload(payload)
                .build();
    }
}
