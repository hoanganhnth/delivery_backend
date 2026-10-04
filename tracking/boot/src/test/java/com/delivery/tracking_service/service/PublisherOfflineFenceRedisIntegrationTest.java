package com.delivery.tracking_service.service;

import com.delivery.tracking.application.*;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.*;
import com.delivery.tracking_service.config.RedisConfig;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.*;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

/** Forces a reconnect after the preliminary generation check and cache read. */
@Testcontainers(disabledWithoutDocker = true)
class PublisherOfflineFenceRedisIntegrationTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    LettuceConnectionFactory factory;
    StringRedisTemplate strings;
    RedisTemplate<String, Object> redis;
    RedisGeoRepository locations;
    ShipperPublisherLeaseRepository leases;
    ShipperLocationEventPublisher events;
    LocationFanoutPublisher fanout;

    @BeforeEach void setup() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet(); factory.start();
        strings = new StringRedisTemplate(factory);
        redis = new RedisConfig().redisTemplate(factory);
        locations = new RedisGeoRepository(redis, strings);
        leases = new ShipperPublisherLeaseRepository(strings);
        events = mock(ShipperLocationEventPublisher.class); fanout = mock(LocationFanoutPublisher.class);
        try (var connection = factory.getConnection()) { connection.serverCommands().flushDb(); }
    }
    @AfterEach void close() { if (factory != null) factory.destroy(); }

    @Test void currentClaimAtomicallyCachesOfflineAndRemovesSerializedMembershipBeforePublishing() {
        locations.cacheShipperLocation(7L, online(10.7), System.currentTimeMillis());
        var claim = expiredClaim();
        assertThat(availability().markOfflineIfExpired(claim)).isTrue();
        var offline = locations.getCachedShipperLocation(7L);
        assertThat(offline.getIsOnline()).isFalse(); assertThat(offline.getLatitude()).isEqualTo(10.7);
        assertThat(strings.getExpire("shipper:location:7")).isBetween(295L, 300L);
        assertThat(redis.opsForSet().isMember("shippers:online:set", "7")).isFalse();
        assertThat(redis.opsForGeo().position("shippers:geo:locations", "7").get(0)).isNull();
        var order = inOrder(events, fanout);
        order.verify(events).publishLocationUpdate(any(), eq("OFFLINE_TOMBSTONE"), org.mockito.ArgumentMatchers.anyLong());
        order.verify(fanout).publish(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void absentCacheStillRemovesStaleMembershipAndPublishesIdentityOnlyOffline() {
        locations.cacheShipperLocation(7L, online(10.7), System.currentTimeMillis()); strings.delete("shipper:location:7");
        assertThat(availability().markOfflineIfExpired(expiredClaim())).isTrue();
        assertThat(locations.getCachedShipperLocation(7L)).isNull();
        assertThat(redis.opsForSet().isMember("shippers:online:set", "7")).isFalse();
        assertThat(redis.opsForGeo().position("shippers:geo:locations", "7").get(0)).isNull();
        var row = org.mockito.ArgumentCaptor.forClass(ShipperLocationResponse.class);
        verify(events).publishLocationUpdate(row.capture(), eq("OFFLINE_TOMBSTONE"), org.mockito.ArgumentMatchers.anyLong());
        assertThat(row.getValue().getShipperId()).isEqualTo(7L);
        assertThat(row.getValue().getLatitude()).isNull(); assertThat(row.getValue().getIsOnline()).isFalse();
        verify(fanout).publish(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void resumedActiveLeaseOrChangedGenerationFencesAnOtherwiseValidClaim() {
        var claim = expiredClaim(); locations.cacheShipperLocation(7L, online(10.9), System.currentTimeMillis());
        strings.opsForValue().set("tracking:publisher:active:7", claim.lease().redisValue());
        assertThat(availability().markOfflineIfExpired(claim)).isFalse();
        strings.delete("tracking:publisher:active:7");
        strings.opsForValue().increment("tracking:publisher:generation:7");
        assertThat(availability().markOfflineIfExpired(claim)).isFalse();
        assertOnlineWithoutEvents();
    }

    @Test void expiredOrReclaimedRecoveryClaimCannotMutateOfflineOrCompleteTheNewClaim() {
        var stale = expiredClaim(); locations.cacheShipperLocation(7L, online(10.9), System.currentTimeMillis());
        String member = "7:" + stale.lease().redisValue();
        var expired = new PublisherExpiryClaim(stale.lease(), System.currentTimeMillis() - 1000);
        strings.opsForZSet().add("tracking:publisher:deadlines", member, expired.claimUntilEpochMillis());
        assertThat(availability().markOfflineIfExpired(expired)).isFalse();
        // Rejected expiry must remain reclaimable, even before another worker has claimed it.
        assertThat(leases.completeClaim(expired)).isFalse();
        assertThat(strings.opsForZSet().score("tracking:publisher:deadlines", member))
                .isEqualTo((double) expired.claimUntilEpochMillis());
        var replacement = leases.claimIfExpired(stale.lease(), 60);
        assertThat(availability().markOfflineIfExpired(stale)).isFalse();
        assertThat(leases.completeClaim(stale)).isFalse();
        assertThat(strings.opsForZSet().score("tracking:publisher:deadlines", member))
                .isEqualTo((double) replacement.claimUntilEpochMillis());
        assertOnlineWithoutEvents();
    }

    @Test void corruptMembershipFailsBeforeAnyCacheOrGeoMutationAndRetainsClaimForRetry() {
        var claim = expiredClaim(); locations.cacheShipperLocation(7L, online(10.9), System.currentTimeMillis());
        strings.delete("shippers:online:set"); strings.opsForValue().set("shippers:online:set", "corrupt");
        assertThatThrownBy(() -> availability().markOfflineIfExpired(claim))
                .hasStackTraceContaining("Invalid shipper membership type");
        assertThat(locations.getCachedShipperLocation(7L).getIsOnline()).isTrue();
        assertThat(redis.opsForGeo().position("shippers:geo:locations", "7").get(0)).isNotNull();
        assertThat(strings.opsForZSet().score("tracking:publisher:deadlines", "7:" + claim.lease().redisValue()))
                .isEqualTo((double) claim.claimUntilEpochMillis());
        verifyNoInteractions(events, fanout);
    }

    private DefaultShipperAvailabilityUseCase availability() {
        var adapter = new RedisShipperAvailabilityAdapter(locations, events, fanout);
        return new DefaultShipperAvailabilityUseCase(adapter, adapter);
    }
    private PublisherExpiryClaim expiredClaim() {
        var lease = leases.acquire(7L, "expired", 30); leases.releaseForGraceIfCurrent(lease, 0);
        strings.opsForZSet().add("tracking:publisher:deadlines", "7:" + lease.redisValue(), System.currentTimeMillis() - 1000);
        return leases.claimIfExpired(lease, 30);
    }
    private void assertOnlineWithoutEvents() {
        assertThat(locations.getCachedShipperLocation(7L).getIsOnline()).isTrue();
        assertThat(locations.getCachedShipperLocation(7L).getLatitude()).isEqualTo(10.9);
        assertThat(redis.opsForSet().isMember("shippers:online:set", "7")).isTrue();
        assertThat(redis.opsForGeo().position("shippers:geo:locations", "7").get(0)).isNotNull();
        verifyNoInteractions(events, fanout);
    }

    @Test void reconnectBetweenAdmissionAndMutationKeepsNewPublisherOnlineAndPublishesNoOldOffline() throws Exception {
        var read = new CountDownLatch(1); var resume = new CountDownLatch(1);
        var adapter = new RedisShipperAvailabilityAdapter(locations, events, fanout);
        ShipperAvailabilityStorePort paused = new ShipperAvailabilityStorePort() {
            public Optional<CachedShipperLocation> findCached(Long id) {
                var cached = adapter.findCached(id); read.countDown();
                try { if (!resume.await(10, TimeUnit.SECONDS)) throw new AssertionError("Reconnect did not finish"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return cached;
            }
            public boolean applyOfflineIfExpired(com.delivery.tracking.domain.PublisherExpiryClaim claim, Optional<OfflineShipperLocation> row, java.time.Instant occurredAt) {
                return adapter.applyOfflineIfExpired(claim, row, occurredAt);
            }
            public void saveOffline(Long id, OfflineShipperLocation row) { adapter.saveOffline(id, row); }
            public void remove(Long id, java.time.Instant occurredAt) { adapter.remove(id, occurredAt); }
        };
        var incidents = mock(PublisherLeaseIncidentPort.class);
        var core = new DefaultPublisherSessionUseCase(leases, new DefaultShipperAvailabilityUseCase(paused, adapter),
                (task, deadline) -> {}, incidents, 0, 30, 30);
        var old = core.acquire(7L, "old"); locations.cacheShipperLocation(7L, online(10.7), System.currentTimeMillis());
        core.disconnected(old);
        String oldMember = "7:" + old.redisValue();
        await().atMost(Duration.ofSeconds(5)).until(() -> strings.opsForZSet().score("tracking:publisher:deadlines", oldMember)
                <= System.currentTimeMillis());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var sweep = executor.submit(() -> core.sweepExpired(10));
            assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
            // Independent lease adapter represents a different publisher instance.
            var replacement = new ShipperPublisherLeaseRepository(strings).acquire(7L, "replacement", 30);
            locations.cacheShipperLocation(7L, online(10.9), System.currentTimeMillis());
            resume.countDown(); sweep.get(10, TimeUnit.SECONDS);
            assertThat(locations.getCachedShipperLocation(7L).getIsOnline()).isTrue();
            assertThat(locations.getCachedShipperLocation(7L).getLatitude()).isEqualTo(10.9);
            assertThat(redis.opsForSet().isMember("shippers:online:set", "7")).isTrue();
            assertThat(redis.opsForGeo().position("shippers:geo:locations", "7").get(0)).isNotNull();
            assertThat(strings.opsForValue().get("tracking:publisher:active:7")).isEqualTo(replacement.redisValue());
            assertThat(strings.opsForZSet().score("tracking:publisher:deadlines", "7:" + replacement.redisValue())).isNotNull();
            assertThat(strings.opsForZSet().score("tracking:publisher:deadlines", oldMember)).isNull();
            verifyNoInteractions(events, fanout, incidents);
        } finally { resume.countDown(); executor.shutdownNow(); }
    }
    private ShipperLocationResponse online(double latitude) {
        var row = new ShipperLocationResponse(); row.setShipperId(7L); row.setLatitude(latitude);
        row.setLongitude(106.7); row.setIsOnline(true); return row;
    }
}
