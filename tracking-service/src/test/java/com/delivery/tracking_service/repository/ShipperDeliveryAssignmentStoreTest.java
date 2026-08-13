package com.delivery.tracking_service.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
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
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenAnswer(invocation -> executeAssignmentScript(
                        invocation.getArgument(0), state, invocation.getArguments()));
        ShipperDeliveryAssignmentStore store = new ShipperDeliveryAssignmentStore(redis);

        store.busy(42L, 100L, 1_000L, "new");
        store.busy(42L, 99L, 900L, "stale");
        store.available(42L, 100L, 999L);
        assertThat(store.activeDelivery(42L)).contains(100L);

        store.available(42L, 100L, 1_000L);
        assertThat(store.activeDelivery(42L)).isEmpty();
    }

    @Test
    void conflictingBusyEventsAtSameTimestampFailClosed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        AtomicReference<String> state = new AtomicReference<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenAnswer(ignored -> state.get());
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenAnswer(invocation -> executeAssignmentScript(
                        invocation.getArgument(0), state, invocation.getArguments()));
        ShipperDeliveryAssignmentStore store = new ShipperDeliveryAssignmentStore(redis);

        store.busy(42L, 100L, 1_000L, "first");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> store.busy(42L, 101L, 1_000L, "contradictory"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.activeDelivery(42L)).contains(100L);
    }

    @SuppressWarnings("unchecked")
    private Long executeAssignmentScript(RedisScript<Long> script, AtomicReference<String> state,
                                         Object[] arguments) {
        String source = script.getScriptAsString();
        if (source.contains("incomingTimestamp")) {
            String deliveryId = String.valueOf(arguments[2]);
            long timestamp = Long.parseLong(String.valueOf(arguments[3]));
            String eventId = String.valueOf(arguments[4]);
            String current = state.get();
            if (current != null) {
                String[] fields = current.split("\\|", 3);
                long currentTimestamp = Long.parseLong(fields[1]);
                if (currentTimestamp > timestamp) return 0L;
                if (currentTimestamp == timestamp) {
                    return fields[0].equals(deliveryId) && fields[2].equals(eventId) ? 0L : -1L;
                }
            }
            state.set(deliveryId + "|" + timestamp + "|" + eventId);
            return 1L;
        }
        String current = state.get();
        if (current == null) return 0L;
        String[] fields = current.split("\\|", 3);
        long timestamp = Long.parseLong(String.valueOf(arguments[3]));
        if (!fields[0].equals(String.valueOf(arguments[2])) || Long.parseLong(fields[1]) > timestamp) {
            return 0L;
        }
        state.set(null);
        return 1L;
    }
}
