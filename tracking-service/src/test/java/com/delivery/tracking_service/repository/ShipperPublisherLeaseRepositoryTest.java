package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.websocket.PublisherLease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperPublisherLeaseRepositoryTest {

    @Mock StringRedisTemplate redisTemplate;

    @Test
    void offlineGraceFenceChecksGenerationAndAbsenceOfActivePublisher() {
        ShipperPublisherLeaseRepository repository = new ShipperPublisherLeaseRepository(redisTemplate);
        PublisherLease lease = new PublisherLease(7L, "session-1", 3L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), eq("3")))
                .thenReturn(1L);

        assertThat(repository.shouldMarkOfflineAfterGrace(lease)).isTrue();

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("tracking:publisher:generation:7", "tracking:publisher:active:7")),
                eq("3"));
    }
}
