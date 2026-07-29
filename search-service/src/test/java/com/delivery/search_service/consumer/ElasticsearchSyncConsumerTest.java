package com.delivery.search_service.consumer;

import com.delivery.search_service.document.DishDocument;
import com.delivery.search_service.document.RestaurantDocument;
import com.delivery.search_service.dto.EntitySyncEvent;
import com.delivery.search_service.document.EntitySyncCheckpoint;
import com.delivery.search_service.repository.DishSearchRepository;
import com.delivery.search_service.repository.RestaurantSearchRepository;
import com.delivery.search_service.repository.EntitySyncCheckpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.UUID;

class ElasticsearchSyncConsumerTest {

    @Test
    void invalidEventFailsSoKafkaCanRetryAndDeadLetterIt() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        EntitySyncCheckpointRepository checkpoints = mock(EntitySyncCheckpointRepository.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(
                restaurants, dishes, checkpoints, new ObjectMapper());
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

        verifyNoInteractions(restaurants, dishes, checkpoints);
    }

    @Test
    void staleReplayCannotOverwriteNewerSearchDocument() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        EntitySyncCheckpointRepository checkpoints = mock(EntitySyncCheckpointRepository.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(
                restaurants, dishes, checkpoints, new ObjectMapper());
        LocalDateTime oldTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        EntitySyncEvent event = EntitySyncEvent.builder()
                .eventId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .occurredAt(oldTime)
                .entityType("RESTAURANT").entityId("1").action("UPDATE")
                .payload(java.util.Map.of("name", "Old"))
                .build();
        when(checkpoints.findById("RESTAURANT:1")).thenReturn(java.util.Optional.of(
                EntitySyncCheckpoint.builder()
                        .id("RESTAURANT:1")
                        .eventId("22222222-2222-2222-2222-222222222222")
                        .occurredAt(oldTime.plusMinutes(1))
                        .action("UPDATE")
                        .build()));

        consumer.consumeEntitySyncEvent(event);

        verify(checkpoints).findById("RESTAURANT:1");
        verifyNoInteractions(restaurants, dishes);
        verify(checkpoints, never()).save(any());
    }

    @Test
    void exactReplayCanReapplyDocumentAfterCheckpointBeforeMutationCrash() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        RestaurantSearchRepository restaurantRepository = mock(RestaurantSearchRepository.class);
        EntitySyncCheckpointRepository checkpoints = mock(EntitySyncCheckpointRepository.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(
                restaurants, dishes, checkpoints, new ObjectMapper());
        when(restaurants.getIfAvailable()).thenReturn(restaurantRepository);
        EntitySyncEvent event = event(java.util.Map.of("name", "Canonical"));

        ArgumentCaptor<EntitySyncCheckpoint> saved = ArgumentCaptor.forClass(EntitySyncCheckpoint.class);
        when(checkpoints.findById("RESTAURANT:1"))
                .thenReturn(java.util.Optional.empty())
                .thenAnswer(ignored -> java.util.Optional.of(saved.getValue()));

        consumer.consumeEntitySyncEvent(event);
        verify(checkpoints).save(saved.capture());

        assertDoesNotThrow(() -> consumer.consumeEntitySyncEvent(event));
        verify(restaurantRepository, times(2)).save(any(RestaurantDocument.class));
    }

    @Test
    void sameEventIdWithChangedPayloadIsRejected() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        RestaurantSearchRepository restaurantRepository = mock(RestaurantSearchRepository.class);
        EntitySyncCheckpointRepository checkpoints = mock(EntitySyncCheckpointRepository.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(
                restaurants, dishes, checkpoints, new ObjectMapper());
        when(restaurants.getIfAvailable()).thenReturn(restaurantRepository);
        EntitySyncEvent original = event(java.util.Map.of("name", "Canonical"));
        EntitySyncEvent contradiction = event(java.util.Map.of("name", "Tampered"));

        ArgumentCaptor<EntitySyncCheckpoint> saved = ArgumentCaptor.forClass(EntitySyncCheckpoint.class);
        when(checkpoints.findById("RESTAURANT:1"))
                .thenReturn(java.util.Optional.empty())
                .thenAnswer(ignored -> java.util.Optional.of(saved.getValue()));
        consumer.consumeEntitySyncEvent(original);
        verify(checkpoints).save(saved.capture());

        assertThrows(IllegalArgumentException.class,
                () -> consumer.consumeEntitySyncEvent(contradiction));
        verify(restaurantRepository, times(1)).save(any(RestaurantDocument.class));
    }

    @Test
    void exactReplayUpgradesLegacyCheckpointWithPayloadFingerprint() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        RestaurantSearchRepository restaurantRepository = mock(RestaurantSearchRepository.class);
        EntitySyncCheckpointRepository checkpoints = mock(EntitySyncCheckpointRepository.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(
                restaurants, dishes, checkpoints, new ObjectMapper());
        when(restaurants.getIfAvailable()).thenReturn(restaurantRepository);
        EntitySyncEvent event = event(java.util.Map.of("name", "Canonical"));
        EntitySyncCheckpoint legacy = EntitySyncCheckpoint.builder()
                .id("RESTAURANT:1")
                .eventId(event.getEventId().toString())
                .occurredAt(event.getOccurredAt())
                .action("UPDATE")
                .build();
        when(checkpoints.findById("RESTAURANT:1"))
                .thenReturn(java.util.Optional.of(legacy));

        consumer.consumeEntitySyncEvent(event);

        verify(checkpoints).save(argThat(checkpoint ->
                checkpoint == legacy
                        && checkpoint.getPayloadFingerprint() != null
                        && !checkpoint.getPayloadFingerprint().isBlank()));
        verify(restaurantRepository).save(any(RestaurantDocument.class));
    }

    @Test
    void removedShipperSearchEventIsRejectedBeforeCheckpointOrDocumentMutation() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        EntitySyncCheckpointRepository checkpoints = mock(EntitySyncCheckpointRepository.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(
                restaurants, dishes, checkpoints, new ObjectMapper());
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

        verifyNoInteractions(restaurants, dishes, checkpoints);
    }

    @Test
    void missingRepositoryFailsAfterCheckpointSoKafkaCanRetryProjection() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        EntitySyncCheckpointRepository checkpoints = mock(EntitySyncCheckpointRepository.class);
        ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(
                restaurants, dishes, checkpoints, new ObjectMapper());
        EntitySyncEvent event = event(java.util.Map.of("name", "Retry me"));

        assertThrows(IllegalStateException.class,
                () -> consumer.consumeEntitySyncEvent(event));

        verify(checkpoints).save(any(EntitySyncCheckpoint.class));
        verify(restaurants).getIfAvailable();
        verifyNoInteractions(dishes);
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
