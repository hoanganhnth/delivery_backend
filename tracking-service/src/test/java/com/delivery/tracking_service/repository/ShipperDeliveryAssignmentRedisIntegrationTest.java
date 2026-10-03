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

    @Test
    void actualCoreKeepsNewerLocalRoomWhenRedisRejectsStaleBusyOrAvailable() {
        var rooms = new com.delivery.tracking_service.websocket.DeliveryRoomRegistry();
        var core = new com.delivery.tracking.application.DefaultDeliveryRoomAssignmentUseCase(assignments,rooms);
        var id = "00000000-0000-0000-0000-000000000001";
        core.apply(new com.delivery.tracking.application.api.DeliveryRoomAssignmentCommand(42,200,7,2000,id,"BUSY",false));
        rooms.subscribe(200,42,"participant");
        core.apply(new com.delivery.tracking.application.api.DeliveryRoomAssignmentCommand(42,100,7,1000,id,"BUSY",false));
        assertThat(rooms.activeDelivery(42)).isEqualTo(200);
        assertThat(rooms.subscribers(200,42)).containsExactly("participant");
        core.apply(new com.delivery.tracking.application.api.DeliveryRoomAssignmentCommand(42,200,7,1999,id,"AVAILABLE",false));
        assertThat(rooms.subscribers(200,42)).containsExactly("participant");
        assertThat(assignments.activeDelivery(42)).contains(200L);
        core.apply(new com.delivery.tracking.application.api.DeliveryRoomAssignmentCommand(42,200,7,2000,id,"AVAILABLE",false));
        assertThat(rooms.subscribersForShipper(42)).isEmpty();
        assertThat(assignments.activeDelivery(42)).isEmpty();
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
