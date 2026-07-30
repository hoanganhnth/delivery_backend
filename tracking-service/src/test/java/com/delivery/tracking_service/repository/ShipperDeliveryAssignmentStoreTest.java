package com.delivery.tracking_service.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShipperDeliveryAssignmentStoreTest {

    @Test
    void staleBusyOrAvailableCannotReplaceNewerAssignment() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        AtomicReference<String> state = new AtomicReference<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenAnswer(ignored -> state.get());
        doAnswer(invocation -> {
            state.set(invocation.getArgument(1));
            return null;
        }).when(values).set(any(), any(), any(java.time.Duration.class));
        when(redis.delete(any(String.class))).thenAnswer(ignored -> {
            state.set(null);
            return true;
        });
        ShipperDeliveryAssignmentStore store = new ShipperDeliveryAssignmentStore(redis);

        store.busy(42L, 100L, 1_000L, "new");
        store.busy(42L, 99L, 900L, "stale");
        store.available(42L, 100L, 999L);
        assertThat(store.activeDelivery(42L)).contains(100L);

        store.available(42L, 100L, 1_000L);
        assertThat(store.activeDelivery(42L)).isEmpty();
    }
}
