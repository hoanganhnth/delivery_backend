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


    @Test
    void fingerprintStillIncludesDeletionAndVersionFieldsWithSortedNestedPayload() throws Exception {
        var checkpoints = mock(EntitySyncCheckpointStore.class);
        var projections = mock(SearchProjectionWriter.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var consumer = new ElasticsearchSyncConsumer(checkpoints, projections, mapper);
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("z", 1); payload.put("a", java.util.Map.of("z", 2, "a", 3));
        var event = event(payload);
        event.setEntityType("restaurant"); event.setAction("delete"); event.setAggregateVersion(7L);
        event.setDeletedAt(event.getOccurredAt()); event.setDeletionReason("archived");
        consumer.consumeEntitySyncEvent(event);
        var canonical = new java.util.TreeMap<String, Object>();
        canonical.put("action", "DELETE"); canonical.put("entityType", "RESTAURANT");
        canonical.put("entityId", "1"); canonical.put("occurredAt", event.getOccurredAt().toString());
        canonical.put("aggregateVersion", 7L); canonical.put("deletedAt", event.getDeletedAt());
        canonical.put("deletionReason", "archived"); canonical.put("payload", payload);
        String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(mapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .writeValueAsBytes(canonical)));
        verify(checkpoints).claim(event, expected);
        verify(projections).apply(event);
    }

    @Test
    void fingerprintFailureOccursAfterAdmissionAndBeforeClaim() {
        var checkpoints = mock(EntitySyncCheckpointStore.class);
        var projections = mock(SearchProjectionWriter.class);
        var consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        var failure = assertThrows(IllegalArgumentException.class,
                () -> consumer.consumeEntitySyncEvent(event(java.util.Map.of("unserializable", new Object()))));
        org.junit.jupiter.api.Assertions.assertEquals("entity-sync payload cannot be fingerprinted", failure.getMessage());
        org.junit.jupiter.api.Assertions.assertNotNull(failure.getCause());
        verifyNoInteractions(checkpoints, projections);
    }

    @Test
    void nullWireEventFailsBeforeAnySideEffect() {
        var checkpoints = mock(EntitySyncCheckpointStore.class);
        var projections = mock(SearchProjectionWriter.class);
        var consumer = new ElasticsearchSyncConsumer(checkpoints, projections, new ObjectMapper());
        var failure = assertThrows(IllegalArgumentException.class, () -> consumer.consumeEntitySyncEvent(null));
        org.junit.jupiter.api.Assertions.assertEquals(
                "stable eventId, occurredAt, entityType, action and entityId are required", failure.getMessage());
        verifyNoInteractions(checkpoints, projections);
    }

    @Test
    void fingerprintIgnoresEventIdentityAndLabelCaseButIncludesAllProjectionValues() {
        var checkpoints = mock(EntitySyncCheckpointStore.class);
        var projections = mock(SearchProjectionWriter.class);
        var consumer = new ElasticsearchSyncConsumer(checkpoints, projections,
                new ObjectMapper().findAndRegisterModules());
        var original = event(java.util.Map.of("name", "Canonical"));
        consumer.consumeEntitySyncEvent(original);
        var fingerprints = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(checkpoints).claim(same(original), fingerprints.capture());
        String baseline = fingerprints.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(
                "31ca8fb60470de121e947a15212f5640ab04389d49162e9303c7d490080144b5", baseline);

        var equivalent = event(java.util.Map.of("name", "Canonical"));
        equivalent.setEventId(UUID.randomUUID());
        equivalent.setAction("update"); equivalent.setEntityType("restaurant");
        consumer.consumeEntitySyncEvent(equivalent);
        verify(checkpoints).claim(same(equivalent), eq(baseline));

        java.util.List<java.util.function.Consumer<EntitySyncEvent>> changes = java.util.List.of(
                event -> event.setAction("CREATE"), event -> event.setEntityType("DISH"),
                event -> event.setEntityId("2"), event -> event.setOccurredAt(event.getOccurredAt().plusNanos(1)),
                event -> event.setAggregateVersion(2L), event -> event.setDeletedAt(event.getOccurredAt()),
                event -> event.setDeletionReason("reason"), event -> event.setPayload(java.util.Map.of("name", "Changed")));
        for (var change : changes) {
            var changed = event(java.util.Map.of("name", "Canonical"));
            change.accept(changed);
            consumer.consumeEntitySyncEvent(changed);
            var captured = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(checkpoints).claim(same(changed), captured.capture());
            org.junit.jupiter.api.Assertions.assertNotEquals(baseline, captured.getValue());
        }
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
