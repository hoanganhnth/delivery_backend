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

    @Test
    void terminalLegacyAssignmentRejectsLateBusyButAllowsTheNextDelivery() {
        assignments.busy(42,100,1000,"initial"); assignments.available(42,100,2000);
        assignments.busy(42,100,1500,"late");
        assertThat(assignments.activeDeliveries(42)).isEmpty();
        assignments.busy(42,100,2000,"terminal-tie");
        assertThat(assignments.activeDeliveries(42)).isEmpty();
        assignments.busy(42,200,3000,"next");
        assignments.available(42,100,3500);
        assertThat(assignments.activeDelivery(42)).contains(200L);
        assignments.available(42,200,3500); assignments.busy(42,200,3200,"late-next");
        assertThat(assignments.activeDeliveries(42)).isEmpty();
        assignments.busy(42,300,4000,"later");
        assertThat(assignments.activeDelivery(42)).contains(300L);
    }

    @Test
    void terminalBatchItemCannotResurrectAndDoesNotCloseItsSibling() {
        assignments.busyBatch(42,100,1000,"first"); assignments.busyBatch(42,101,1000,"sibling");
        assignments.availableBatch(42,100,2000); assignments.busyBatch(42,100,1500,"late");
        assertThat(assignments.activeDeliveries(42)).containsExactly(101L);
        assignments.busyBatch(42,100,2000,"terminal-tie");
        assertThat(assignments.activeDeliveries(42)).containsExactly(101L);
        assignments.availableBatch(42,101,2000);
        assertThat(assignments.activeDeliveries(42)).isEmpty();
        assignments.busyBatch(42,100,3000,"newer");
        assertThat(assignments.activeDeliveries(42)).containsExactly(100L);
    }

    @Test
    void availableBeforeInitialBusyRetainsTerminalFenceForLegacyAndBatch() {
        assignments.available(42,100,2000); assignments.busy(42,100,1500,"late");
        assertThat(assignments.activeDelivery(42)).isEmpty();
        assignments.availableBatch(43,101,2000); assignments.busyBatch(43,101,1500,"late-batch");
        assertThat(assignments.activeDeliveries(43)).isEmpty();
    }

    @Test
    void independentWritersConvergeWhenTerminalAndLateBusyRace() throws Exception {
        var second=new ShipperDeliveryAssignmentStore(redis);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for(boolean batch:java.util.List.of(false,true)) for(int i=0;i<12;i++) {
                long shipper=1000+i+(batch?100:0);
                var start=new java.util.concurrent.CountDownLatch(1);
                var busy=executor.submit(()->{start.await();if(batch)assignments.busyBatch(shipper,100,1500,"late");else assignments.busy(shipper,100,1500,"late");return null;});
                var available=executor.submit(()->{start.await();if(batch)second.availableBatch(shipper,100,2000);else second.available(shipper,100,2000);return null;});
                start.countDown(); busy.get(10,java.util.concurrent.TimeUnit.SECONDS); available.get(10,java.util.concurrent.TimeUnit.SECONDS);
                assertThat(assignments.activeDeliveries(shipper)).isEmpty();
            }
        } finally {executor.shutdownNow();}
    }

    @Test
    void terminalKeysUseExistingTtlAndCorruptionFailsBeforeMutation() {
        assignments.available(42,100,2000); assignments.availableBatch(43,101,2000);
        String legacy="tracking:shipper:assignment-terminal:42";
        String batch="tracking:shipper:batch-assignment-terminal:43:101";
        assertThat(redis.opsForValue().get(legacy)).isEqualTo("2000");
        assertThat(redis.opsForValue().get(batch)).isEqualTo("2000");
        assertThat(redis.getExpire(legacy)).isBetween(86300L,86400L);
        assertThat(redis.getExpire(batch)).isBetween(86300L,86400L);
        assignments.available(42,100,1500); assignments.availableBatch(43,101,1500);
        assertThat(redis.opsForValue().get(legacy)).isEqualTo("2000");
        assertThat(redis.opsForValue().get(batch)).isEqualTo("2000");
        redis.opsForValue().set(legacy,"corrupt"); redis.opsForValue().set(batch,"corrupt");
        assertThatThrownBy(()->assignments.busy(42,100,3000,"next")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->assignments.available(42,100,3000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->assignments.busyBatch(43,101,3000,"next")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->assignments.availableBatch(43,101,3000)).isInstanceOf(IllegalStateException.class);
        assertThat(assignments.activeDeliveries(42)).isEmpty();
        assertThat(assignments.activeDeliveries(43)).isEmpty();
    }

    @Test
    void localSubscriptionAndAssignmentUpdateCannotInterleaveProjectionReadAndRoomMutation() throws Exception {
        assignments.busy(42,100,1000,"initial");
        var rooms=new com.delivery.tracking_service.websocket.DeliveryRoomRegistry();
        rooms.subscribe(100,42,"initial");
        var read=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
        var blockedReader=new ShipperDeliveryAssignmentStore(redis) {
            @Override public java.util.Set<Long> activeDeliveries(long shipper) {
                var snapshot=super.activeDeliveries(shipper); read.countDown();
                try {if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("subscription release timeout");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
                return snapshot;
            }
        };
        var subscription=new com.delivery.tracking.application.DefaultDeliveryRoomSubscriptionUseCase(blockedReader,rooms);
        var update=new com.delivery.tracking.application.DefaultDeliveryRoomAssignmentUseCase(assignments,rooms);
        var updaterThread=new java.util.concurrent.atomic.AtomicReference<Thread>();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->subscription.subscribeAuthorized(100,42,"late-old"));
            assertThat(read.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second=executor.submit(()->{
                updaterThread.set(Thread.currentThread());
                update.apply(new com.delivery.tracking.application.api.DeliveryRoomAssignmentCommand(42,200,7,2000,
                        "00000000-0000-0000-0000-000000000002","BUSY",false));
            });
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(()->
                        assertThat(updaterThread.get()).isNotNull().satisfies(thread->assertThat(thread.getState()).isEqualTo(Thread.State.BLOCKED)));
            } catch(org.awaitility.core.ConditionTimeoutException missingFence) {
                throw new AssertionError("Assignment was not serialized behind the in-flight subscription",missingFence);
            }
            assertThat(assignments.activeDelivery(42)).contains(100L);
            // Another shipper can progress while this projection read is stalled.
            update.apply(new com.delivery.tracking.application.api.DeliveryRoomAssignmentCommand(43,300,8,2000,
                    "00000000-0000-0000-0000-000000000003","BUSY",false));
            assertThat(rooms.activeDelivery(43)).isEqualTo(300L);
            release.countDown(); first.get(10,java.util.concurrent.TimeUnit.SECONDS); second.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(assignments.activeDelivery(42)).contains(200L);
            assertThat(rooms.activeDelivery(42)).isEqualTo(200L);
            assertThat(rooms.subscribers(100,42)).isEmpty();
        } finally {release.countDown();executor.shutdownNow();}
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
