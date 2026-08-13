package com.delivery.tracking_service.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the cross-replica assignment fence executes atomically in real Redis. */
@SpringJUnitConfig(classes = ShipperDeliveryAssignmentRedisIntegrationTest.RedisConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipperDeliveryAssignmentRedisIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired private ShipperDeliveryAssignmentStore assignments;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void staleAndContradictoryBusyEventsCannotOverwriteNewerAssignment() {
        assignments.busy(42L, 100L, 2_000L, "first");
        assignments.busy(42L, 99L, 1_000L, "stale");
        assertThat(assignments.activeDelivery(42L)).contains(100L);

        assertThatThrownBy(() -> assignments.busy(42L, 101L, 2_000L, "conflicting"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(assignments.activeDelivery(42L)).contains(100L);

        assignments.available(42L, 100L, 1_999L);
        assertThat(assignments.activeDelivery(42L)).contains(100L);
        assignments.available(42L, 100L, 2_000L);
        assertThat(assignments.activeDelivery(42L)).isEmpty();
    }

    @TestConfiguration
    static class RedisConfiguration {
        @Bean
        LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                    REDIS.getHost(), REDIS.getMappedPort(6379)));
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }

        @Bean
        ShipperDeliveryAssignmentStore shipperDeliveryAssignmentStore(StringRedisTemplate redis) {
            return new ShipperDeliveryAssignmentStore(redis);
        }
    }
}
