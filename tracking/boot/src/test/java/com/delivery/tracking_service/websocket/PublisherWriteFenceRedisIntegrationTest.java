package com.delivery.tracking_service.websocket;

import com.delivery.tracking.application.DefaultPublisherSessionUseCase;
import com.delivery.tracking.application.DefaultTrackingService;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.PublisherLease;
import com.delivery.tracking.domain.Coordinate;
import com.delivery.tracking.domain.LocationUpdateSource;
import com.delivery.tracking_service.config.RedisConfig;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.*;
import com.delivery.tracking_service.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class PublisherWriteFenceRedisIntegrationTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    LettuceConnectionFactory factory;
    StringRedisTemplate strings;
    RedisGeoRepository locations;
    ShipperPublisherLeaseRepository leases;
    @BeforeEach void setup() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet(); factory.start(); strings = new StringRedisTemplate(factory);
        locations = new RedisGeoRepository(new RedisConfig().redisTemplate(factory), strings);
        leases = new ShipperPublisherLeaseRepository(strings);
        try (var connection = factory.getConnection()) { connection.serverCommands().flushDb(); }
    }
    @AfterEach void close() { if (factory != null) factory.destroy(); }

    @Test void currentPublisherAtomicallyWritesOnlineAndOfflineWithExistingSerializationAndTtl() {
        var lease = leases.acquire(7L, "current", 30);
        var events = mock(ShipperLocationEventPublisher.class); var fanout = mock(LocationFanoutPublisher.class);
        var adapter = new RedisLocationUpdateAdapter(locations, events, fanout);
        var core = new DefaultTrackingService(adapter, adapter);
        for (boolean online : new boolean[]{true, false}) {
            var result = core.updatePublisherLocation(command(10.9, online), lease).orElseThrow();
            var cached = locations.getCachedShipperLocation(7L);
            assertThat(cached.getLatitude()).isEqualTo(10.9); assertThat(cached.getIsOnline()).isEqualTo(online);
            assertThat(cached.getAccuracy()).isEqualTo(3.5); assertThat(cached.getSpeed()).isEqualTo(12.0);
            assertThat(cached.getHeading()).isEqualTo(90.0); assertThat(cached.getLastPing()).isNotBlank();
            assertThat(strings.getExpire("shipper:location:7")).isBetween(295L, 300L);
            var redis = new RedisConfig().redisTemplate(factory);
            assertThat(redis.opsForSet().isMember("shippers:online:set", "7")).isEqualTo(online);
            assertThat(redis.opsForGeo().position("shippers:geo:locations", "7").get(0) != null).isEqualTo(online);
            var order = inOrder(events, fanout);
            order.verify(events).publishLocationUpdate(any(), eq("WEBSOCKET"), org.mockito.ArgumentMatchers.anyLong()); order.verify(fanout).publish(any(), org.mockito.ArgumentMatchers.anyLong());
            assertThat(result.online()).isEqualTo(online); clearInvocations(events, fanout);
        }
    }

    @Test void missingActiveLeaseOrWrongSessionCannotWriteOrPublishEvenWhenGenerationMatches() {
        var lease = leases.acquire(7L, "current", 30);
        var events = mock(ShipperLocationEventPublisher.class); var fanout = mock(LocationFanoutPublisher.class);
        var adapter = new RedisLocationUpdateAdapter(locations, events, fanout); var core = new DefaultTrackingService(adapter, adapter);
        assertThat(core.updatePublisherLocation(command(10.7, true), new PublisherLease(7, "other", lease.generation()))).isEmpty();
        strings.delete("tracking:publisher:active:7");
        assertThat(core.updatePublisherLocation(command(10.7, true), lease)).isEmpty();
        assertThat(locations.getCachedShipperLocation(7L)).isNull(); verifyNoInteractions(events, fanout);
    }

    @Test void invalidGeoOrCorruptMembershipFailsBeforeWritingCacheAndPublishing() {
        var lease = leases.acquire(7L, "current", 30);
        var events = mock(ShipperLocationEventPublisher.class); var fanout = mock(LocationFanoutPublisher.class);
        var adapter = new RedisLocationUpdateAdapter(locations, events, fanout); var core = new DefaultTrackingService(adapter, adapter);
        assertThatThrownBy(() -> core.updatePublisherLocation(command(90, true), lease))
                .hasStackTraceContaining("invalid longitude,latitude pair");
        assertThat(locations.getCachedShipperLocation(7L)).isNull();
        assertThat(strings.hasKey("shippers:geo:locations")).isFalse(); assertThat(strings.hasKey("shippers:online:set")).isFalse();
        strings.opsForValue().set("shippers:online:set", "corrupt");
        assertThatThrownBy(() -> core.updatePublisherLocation(command(10.9, true), lease))
                .hasStackTraceContaining("Invalid shipper membership type");
        assertThat(locations.getCachedShipperLocation(7L)).isNull();
        assertThat(strings.hasKey("shippers:geo:locations")).isFalse(); verifyNoInteractions(events, fanout);
    }

    private UpdateLocationCommand command(double latitude, boolean online) {
        return new UpdateLocationCommand(7, new Coordinate(latitude, 106.7), 3.5, 12.0, 90.0, online, LocationUpdateSource.WEBSOCKET);
    }

    @Test void replacementAfterSuccessfulRefreshCannotBeOverwrittenByOldWebSocketWriter() throws Exception {
        var refreshed = new CountDownLatch(1); var resume = new CountDownLatch(1);
        var core = new DefaultPublisherSessionUseCase(leases, mock(ShipperAvailabilityUseCase.class),
                (task, deadline) -> {}, mock(PublisherLeaseIncidentPort.class), 30, 120, 30);
        PublisherSessionUseCase paused = new PublisherSessionUseCase() {
            public PublisherLease acquire(Long id, String session) { return core.acquire(id, session); }
            public boolean refreshIfCurrent(PublisherLease lease) {
                boolean current = core.refreshIfCurrent(lease); refreshed.countDown();
                try { if (!resume.await(10, TimeUnit.SECONDS)) throw new AssertionError("No replacement"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return current;
            }
            public void disconnected(PublisherLease lease) { core.disconnected(lease); }
            public void sweepExpired(int size) { core.sweepExpired(size); }
        };
        var events = mock(ShipperLocationEventPublisher.class);
        var handler = TrackingWebSocketTestFixture.create(locations, events, mock(DeliveryTrackingAccessClient.class), paused);
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("old"); when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("authenticatedUserId", 7L,
                "authenticatedPrincipalId", 1007L, "authenticatedRole", "SHIPPER")));
        handler.afterConnectionEstablished(session);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var write = executor.submit(() -> {
                try { handler.handleTextMessage(session, new TextMessage(
                        "{\"action\":\"update_location\",\"latitude\":10.7,\"longitude\":106.7}")); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            assertThat(refreshed.await(10, TimeUnit.SECONDS)).isTrue();
            var replacement = new ShipperPublisherLeaseRepository(strings).acquire(7L, "new", 120);
            var fresh = new ShipperLocationResponse(); fresh.setShipperId(7L); fresh.setIsOnline(true);
            fresh.setLatitude(10.9); fresh.setLongitude(106.8); locations.cacheShipperLocation(7L, fresh, System.currentTimeMillis());
            resume.countDown(); write.get(10, TimeUnit.SECONDS);
            assertThat(locations.getCachedShipperLocation(7L).getLatitude()).isEqualTo(10.9);
            assertThat(locations.getCachedShipperLocation(7L).getLongitude()).isEqualTo(106.8);
            assertThat(strings.opsForValue().get("tracking:publisher:active:7")).isEqualTo(replacement.redisValue());
            verifyNoInteractions(events);
            verify(session).sendMessage(argThat((TextMessage m) -> m.getPayload().contains("PUBLISHER_SUPERSEDED")));
            verify(session).close(argThat(status -> status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
        } finally { resume.countDown(); executor.shutdownNow(); }
    }
}
