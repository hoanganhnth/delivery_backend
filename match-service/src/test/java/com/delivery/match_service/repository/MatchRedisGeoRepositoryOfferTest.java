package com.delivery.match_service.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchRedisGeoRepositoryOfferTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> values;
    @Mock SetOperations<String, Object> sets;
    @Mock GeoOperations<String, Object> geo;

    private MatchRedisGeoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MatchRedisGeoRepository(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reservesOfferWithAtomicForwardAndReverseOwnership() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("match:shipper:offer:7", "match:delivery:offer:11",
                        "match:cancelled:11")),
                eq("11"), eq("7"), eq(180)))
                .thenReturn(1L);

        assertThat(repository.tryReserveShipperOffer(7L, 11L, 180)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsReservationOwnedByAnotherDelivery() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(),
                eq("11"), eq("7"), eq(180)))
                .thenReturn(0L);

        assertThat(repository.tryReserveShipperOffer(7L, 11L, 180)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelledDeliveryCannotAcquireOfferAfterTombstone() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(),
                eq("11"), eq("7"), eq(180)))
                .thenReturn(-2L);

        assertThat(repository.tryReserveShipperOffer(7L, 11L, 180)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void propagatesRedisFailureInsteadOfReportingReservationRace() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(),
                eq("11"), eq("7"), eq(180)))
                .thenThrow(new RuntimeException("redis unavailable"));

        assertThatThrownBy(() -> repository.tryReserveShipperOffer(7L, 11L, 180))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot reserve shipper offer");
    }

    @Test
    @SuppressWarnings("unchecked")
    void releasesOnlyMatchingForwardAndReverseOwnership() {
        when(values.get("match:delivery:offer:11")).thenReturn("7");
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("match:shipper:offer:7", "match:delivery:offer:11")),
                eq("11"), eq("7")))
                .thenReturn(1L);

        assertThat(repository.releaseOfferForDelivery(11L)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleReleaseCannotDeleteDifferentOfferGeneration() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("match:shipper:offer:7", "match:delivery:offer:11")),
                eq("11"), eq("7")))
                .thenReturn(0L);

        assertThat(repository.releaseShipperOffer(7L, 11L)).isFalse();
    }

    @Test
    void offlineTombstoneRemovesMatchingMembershipAndFencesOlderUpdates() {
        when(values.get("match:shipper:location-fresh:7")).thenReturn(100L);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        when(redisTemplate.opsForGeo()).thenReturn(geo);

        repository.markShipperOffline(7L, 200L);

        verify(sets).remove("match:shippers:online", "7");
        verify(geo).remove("match:shippers:geo", "7");
        verify(values).set("match:shipper:location-fresh:7", 200L, 300L, TimeUnit.SECONDS);
    }

    @Test
    void staleOfflineTombstoneCannotRemoveNewerOnlineState() {
        when(values.get("match:shipper:location-fresh:7")).thenReturn(300L);

        repository.markShipperOffline(7L, 200L);

        verify(redisTemplate, never()).opsForSet();
        verify(redisTemplate, never()).opsForGeo();
        verify(values, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusMutationUsesAtomicVersionOfferAndBusyScript() {
        String eventId = "11111111-1111-1111-1111-111111111111";
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("match:shipper:status-version:7",
                        "match:shipper:offer:7", "match:shipper:busy:7",
                        "match:delivery:offer:11")),
                eq(200L), eq(eventId), eq("11"), eq(1L), eq("7")))
                .thenReturn(1L);

        assertThat(repository.applyShipperStatus(7L, 11L, "BUSY", 200L, eventId)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void exactOrStaleStatusReplayIsAnIdempotentNoOp() {
        String eventId = "11111111-1111-1111-1111-111111111111";
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(),
                eq(200L), eq(eventId), eq("11"), eq(0L), eq("7")))
                .thenReturn(0L);

        assertThat(repository.applyShipperStatus(7L, 11L, "AVAILABLE", 200L, eventId)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameTimestampWithDifferentEventIdFailsClosed() {
        String eventId = "22222222-2222-2222-2222-222222222222";
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(),
                eq(200L), eq(eventId), eq("11"), eq(1L), eq("7")))
                .thenReturn(-1L);

        assertThatThrownBy(() -> repository.applyShipperStatus(7L, 11L, "BUSY", 200L, eventId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same timestamp");
    }
}
