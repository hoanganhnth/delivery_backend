package com.delivery.match_service.service.impl;

import com.delivery.match_service.repository.MatchRedisGeoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchCancellationServiceImplTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> values;
    @Mock MatchRedisGeoRepository repository;

    private MatchCancellationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MatchCancellationServiceImpl(redisTemplate, repository);
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    void persistsTombstoneBeforeReleasingCurrentOffer() {
        service.markCancelled(11L);

        var order = inOrder(values, repository);
        order.verify(values).set("match:cancelled:11", Boolean.TRUE, Duration.ofHours(2));
        order.verify(repository).releaseOfferForDelivery(11L);
    }

    @Test
    void offerReleaseFailurePropagatesForKafkaRetry() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(repository).releaseOfferForDelivery(11L);

        assertThatThrownBy(() -> service.markCancelled(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot persist matching cancellation");
    }
}
