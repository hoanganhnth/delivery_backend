package com.delivery.tracking_service.service;

import com.delivery.tracking.application.DefaultPublisherSessionUseCase;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.*;
import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real Redis fences/TTLs and independent application instances; scheduled callbacks may be lost. */
@SpringJUnitConfig(classes = PublisherLeaseRedisIntegrationTest.RedisConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublisherLeaseRedisIntegrationTest {
    private static final String DEADLINES = "tracking:publisher:deadlines";
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @Autowired StringRedisTemplate redis;
    @Autowired ShipperPublisherLeaseRepository leases;
    private RecordingBoundary boundary;
    private final List<Runnable> tasks = new ArrayList<>();

    @BeforeEach void clean() {
        var connection = redis.getConnectionFactory().getConnection();
        try { connection.serverCommands().flushDb(); } finally { connection.close(); }
        boundary = new RecordingBoundary(); tasks.clear();
    }
    @Test void replacementFencesOldRefreshDisconnectAndScheduledGraceCallbackAcrossInstances() {
        var first = core(1, 30, 1); var second = core(1, 30, 1);
        var old = first.acquire(7L, "first"); var current = second.acquire(7L, "second");
        assertThat(current.generation()).isGreaterThan(old.generation());
        assertThat(first.refreshIfCurrent(old)).isFalse();
        first.disconnected(old);
        assertThat(tasks).isEmpty();
        second.disconnected(current);
        assertThat(tasks).hasSize(1);
        var replacement = first.acquire(7L, "replacement"); tasks.get(0).run();
        assertThat(boundary.offline).isEmpty();
        assertThat(redis.opsForValue().get("tracking:publisher:active:7")).isEqualTo(replacement.redisValue());
        assertThat(first.refreshIfCurrent(replacement)).isTrue();
        assertThat(second.refreshIfCurrent(current)).isFalse();
        // A released publisher has no active key for ACQUIRE to prune; its old grace
        // deadline is safely reclaimed by the existing generation fence at expiry.
        assertThat(redis.opsForZSet().score(DEADLINES, member(current))).isNotNull();
        waitExpired(current); second.sweepExpired(100);
        assertThat(boundary.offline).isEmpty();
        assertThat(redis.opsForValue().get("tracking:publisher:active:7")).isEqualTo(replacement.redisValue());
        assertThat(redis.opsForZSet().score(DEADLINES, member(current))).isNull();
    }
    @Test void persistedGraceDeadlineRecoversWhenOriginalInstanceLosesItsScheduledCallback() {
        var original = core(1, 2, 1); var lease = original.acquire(7L, "lost-callback");
        original.disconnected(lease);
        assertThat(tasks).hasSize(1); assertThat(redis.hasKey("tracking:publisher:active:7")).isFalse();
        waitExpired(lease);
        core(1, 2, 1).sweepExpired(100);
        assertThat(boundary.offline).containsExactly(7L); assertThat(boundary.recovered).containsExactly(lease);
        assertThat(redis.opsForZSet().score(DEADLINES, member(lease))).isNull();
    }
    @Test void actualActiveTtlExpiryRecoversWithoutDisconnectOrInMemoryCallback() {
        var original = core(0, 1, 1); var lease = original.acquire(7L, "no-disconnect");
        original.sweepExpired(100); assertThat(boundary.offline).isEmpty(); assertThat(tasks).isEmpty();
        await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(50))
                .until(() -> !Boolean.TRUE.equals(redis.hasKey("tracking:publisher:active:7")));
        waitExpired(lease); core(0, 1, 1).sweepExpired(100);
        assertThat(boundary.offline).containsExactly(7L); assertThat(redis.opsForZSet().score(DEADLINES, member(lease))).isNull();
    }
    @Test void failedOfflineKeepsClaimAndAnotherInstanceRetriesOnlyAfterClaimTimeout() {
        var first = core(0, 1, 1); var lease = first.acquire(7L, "retry"); waitInactive(lease);
        boundary.failOffline = true; first.sweepExpired(100);
        assertThat(boundary.attempts).isEqualTo(1); assertThat(boundary.failures).hasSize(1);
        assertThat(redis.opsForZSet().score(DEADLINES, member(lease))).isGreaterThan((double) redisNow());
        var second = core(0, 1, 1); second.sweepExpired(100); assertThat(boundary.attempts).isEqualTo(1);
        waitExpired(lease); boundary.failOffline = false; second.sweepExpired(100);
        assertThat(boundary.attempts).isEqualTo(2); assertThat(boundary.offline).containsExactly(7L);
        assertThat(redis.opsForZSet().score(DEADLINES, member(lease))).isNull();
    }
    @Test void staleCompletionCannotDeleteClaimRenewedByAnotherRecoveryAttempt() {
        var lease = core(0, 1, 1).acquire(7L, "claim-fence"); waitInactive(lease);
        var first = leases.claimIfExpired(lease, 1); assertThat(first).isNotNull(); waitExpired(lease);
        var newer = leases.claimIfExpired(lease, 5); assertThat(newer).isNotNull();
        assertThat(leases.completeClaim(first)).isFalse();
        assertThat(redis.opsForZSet().score(DEADLINES, member(lease))).isEqualTo((double) newer.claimUntilEpochMillis());
        assertThat(leases.completeClaim(newer)).isTrue();
    }
    @Test void transportDelayCannotSeparateDurableDeadlineFromRedisActiveTtl() {
        var delayed = delayedTransport();
        var lease = delayed.acquire(7L, "delayed", 30);
        long redisNow = redisNow();
        Long remaining = redis.getExpire("tracking:publisher:active:7", java.util.concurrent.TimeUnit.MILLISECONDS);
        Double deadline = redis.opsForZSet().score(DEADLINES, member(lease));
        assertThat(deadline).isCloseTo((double) (redisNow + remaining), within(100.0));
        assertThat(delayed.refreshIfCurrent(lease, 30)).isTrue();
        redisNow = redisNow();
        remaining = redis.getExpire("tracking:publisher:active:7", java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(redis.opsForZSet().score(DEADLINES, member(lease)))
                .isCloseTo((double) (redisNow + remaining), within(100.0));
    }

    @Test void transportDelayDoesNotConsumeGraceOrRecoveryClaimDuration() {
        var delayed = delayedTransport();
        var lease = delayed.acquire(7L, "delayed-claim", 30);
        assertThat(delayed.releaseForGraceIfCurrent(lease, 1)).isTrue();
        assertThat(redis.opsForZSet().score(DEADLINES, member(lease)) - redisNow()).isBetween(900.0, 1000.0);
        waitExpired(lease);
        var claim = delayed.claimIfExpired(lease, 1);
        assertThat(claim).isNotNull();
        assertThat(claim.claimUntilEpochMillis() - redisNow()).isBetween(900L, 1000L);
        assertThat(leases.completeClaim(claim)).isTrue();
        // Sweep returns the exact Redis-created claim deadline, not a separately computed host value.
        lease = delayed.acquire(8L, "delayed-sweep", 1);
        waitInactive(lease);
        var claims = delayed.claimExpired(10, 1);
        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).claimUntilEpochMillis() - redisNow()).isBetween(900L, 1000L);
        assertThat(leases.completeClaim(claims.get(0))).isTrue();
    }

    private ShipperPublisherLeaseRepository delayedTransport() {
        var transport = new StringRedisTemplate(redis.getConnectionFactory()) {
            @Override public <T> T execute(org.springframework.data.redis.core.script.RedisScript<T> script,
                    List<String> keys, Object... args) {
                // Deterministic network/queue delay after the client has constructed command arguments.
                try { Thread.sleep(400); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                return super.execute(script, keys, args);
            }
        };
        return new ShipperPublisherLeaseRepository(transport);
    }

    private long redisNow() {
        return redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "local t=redis.call('TIME'); return tonumber(t[1])*1000+math.floor(tonumber(t[2])/1000)", Long.class), List.of());
    }

    private DefaultPublisherSessionUseCase core(long grace, long ttl, long claim) {
        return new DefaultPublisherSessionUseCase(leases, boundary, (task, deadline) -> tasks.add(task), boundary, grace, ttl, claim);
    }
    private String member(PublisherLease lease) { return lease.shipperId() + ":" + lease.redisValue(); }
    private void waitExpired(PublisherLease lease) {
        await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(50)).until(() -> {
            Double score = redis.opsForZSet().score(DEADLINES, member(lease));
            return score != null && score <= redisNow();
        });
    }
    private void waitInactive(PublisherLease lease) {
        await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(50))
                .until(() -> !Boolean.TRUE.equals(redis.hasKey("tracking:publisher:active:" + lease.shipperId())));
        waitExpired(lease);
    }
    private static final class RecordingBoundary implements ShipperAvailabilityUseCase, PublisherLeaseIncidentPort {
        final List<Long> offline = new ArrayList<>(); final List<PublisherLease> recovered = new ArrayList<>();
        final List<Exception> failures = new ArrayList<>(); boolean failOffline; int attempts;
        public boolean markOfflineIfExpired(PublisherExpiryClaim claim) {
            markOfflineAndBroadcast(claim.lease().shipperId()); return true;
        }
        public OfflineShipperLocation markOfflineAndBroadcast(Long id) {
            attempts++; if (failOffline) throw new IllegalStateException("Broker unavailable"); offline.add(id);
            return new OfflineShipperLocation(new CachedShipperLocation(id, null, null, null, null, null, null), Instant.now());
        }
        public OfflineShipperLocation markOffline(Long id) { throw new AssertionError("Recovery must include distributed fanout"); }
        public void graceFailed(PublisherLease lease, Exception failure) { failures.add(failure); }
        public void sweepFailed(PublisherLease lease, Exception failure) { failures.add(failure); }
        public void expiredOffline(PublisherLease lease) { recovered.add(lease); }
    }
    @TestConfiguration static class RedisConfiguration {
        @Bean LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        }
        @Bean StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) { return new StringRedisTemplate(factory); }
        @Bean ShipperPublisherLeaseRepository publisherLeases(StringRedisTemplate redis) { return new ShipperPublisherLeaseRepository(redis); }
    }
}
