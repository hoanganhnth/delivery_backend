package com.delivery.api_gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import reactor.core.publisher.Flux;

/** Verifies the Lua counter's atomic behavior against a real Redis server. */
@Testcontainers(disabledWithoutDocker = true)
class RedisFixedWindowRateLimitStoreIntegrationTest {
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;

    @AfterEach
    void closeConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void enforcesBoundaryAtomicallyForConcurrentRequests() {
        RedisFixedWindowRateLimitStore store = store();
        String key = key();

        List<RateLimitStore.Decision> decisions = Flux.range(0, 40)
                .flatMap(ignored -> store.increment(key, 10, 30), 40)
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(decisions).hasSize(40);
        assertThat(decisions).filteredOn(decision -> decision.allowed(10)).hasSize(10);
        assertThat(decisions).extracting(RateLimitStore.Decision::count)
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 40).boxed().toList());
    }

    @Test
    void startsANewCounterAfterTheWindowExpires() {
        RedisFixedWindowRateLimitStore store = store();
        String key = key();

        assertThat(store.increment(key, 1, 1).block(Duration.ofSeconds(5)).allowed(1)).isTrue();
        assertThat(store.increment(key, 1, 1).block(Duration.ofSeconds(5)).allowed(1)).isFalse();

        awaitWindowReset(key);

        RateLimitStore.Decision afterReset = store.increment(key, 1, 1).block(Duration.ofSeconds(5));
        assertThat(afterReset.count()).isEqualTo(1);
        assertThat(afterReset.allowed(1)).isTrue();
    }

    private RedisFixedWindowRateLimitStore store() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        return new RedisFixedWindowRateLimitStore(new ReactiveStringRedisTemplate(connectionFactory));
    }

    private void awaitWindowReset(String key) {
        StringRedisTemplate blockingTemplate = new StringRedisTemplate(connectionFactory);
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (blockingTemplate.hasKey(key) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for Redis window expiry", exception);
            }
        }
        assertThat(blockingTemplate.hasKey(key)).isFalse();
    }

    private String key() {
        return "delivery:gateway:rate-limit:test:" + UUID.randomUUID();
    }
}
