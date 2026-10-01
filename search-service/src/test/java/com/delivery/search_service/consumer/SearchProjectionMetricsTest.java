package com.delivery.search_service.consumer;

import com.delivery.observability.Phase8Metrics;
import com.delivery.search_service.dto.EntitySyncEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchProjectionMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EntitySyncCheckpointStore checkpoints = mock(EntitySyncCheckpointStore.class);
    private final SearchProjectionWriter writer = mock(SearchProjectionWriter.class);
    private final ElasticsearchSyncConsumer consumer = new ElasticsearchSyncConsumer(checkpoints, writer,
            new ObjectMapper(), new Phase8Metrics(registry, "search-service"));
    private final EntitySyncEvent tombstone = EntitySyncEvent.builder().eventId(UUID.randomUUID())
            .entityType("DISH").entityId("10").action("DELETE").occurredAt(LocalDateTime.now()).build();

    @Test
    void staleTombstoneCountsRejectionWithoutApplication() {
        when(checkpoints.claim(eq(tombstone), anyString())).thenReturn(EntitySyncCheckpointStore.ClaimResult.STALE);
        consumer.consumeEntitySyncEvent(tombstone);
        assertThat(counter("delivery.events", "outcome", "stale_rejected")).isEqualTo(1);
        assertThat(counter("delivery.tombstones", "action", "applied")).isZero();
        verifyNoInteractions(writer);
    }

    @Test
    void failedProjectionCountsFailureAndSuccessfulExactReplayCountsApplication() {
        when(checkpoints.claim(eq(tombstone), anyString())).thenReturn(EntitySyncCheckpointStore.ClaimResult.APPLY,
                EntitySyncCheckpointStore.ClaimResult.EXACT_REPLAY);
        doThrow(new IllegalStateException("storage unavailable")).doNothing().when(writer).apply(tombstone);
        assertThatThrownBy(() -> consumer.consumeEntitySyncEvent(tombstone)).isInstanceOf(IllegalStateException.class);
        assertThat(counter("delivery.tombstones", "action", "applied")).isZero();
        consumer.consumeEntitySyncEvent(tombstone);
        assertThat(counter("delivery.projections", "outcome", "replay_failure")).isEqualTo(1);
        assertThat(counter("delivery.tombstones", "action", "applied")).isEqualTo(1);
        verify(writer, times(2)).apply(tombstone);
    }

    private double counter(String name, String tag, String outcome) {
        return registry.counter(name, "service", "search-service", tag, outcome).count();
    }
}
