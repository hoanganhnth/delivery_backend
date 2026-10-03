package com.delivery.tracking_service.service;

import com.delivery.tracking.application.DefaultShipperIdentityUseCase;
import com.delivery.tracking.application.DefaultShipperIdentityInboxUseCase;
import com.delivery.identity.contracts.ShipperIdentityUpserted;
import com.delivery.tracking_service.repository.ShipperIdentityInboxReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.concurrent.*;
import static org.awaitility.Awaitility.await;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.Coordinate;
import com.delivery.tracking.domain.LocationSnapshot;
import com.delivery.tracking.domain.PublisherLease;
import com.delivery.tracking_service.entity.ShipperIdentityProjection;
import com.delivery.tracking_service.repository.ShipperIdentityProjectionRepository;
import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository;
import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real projection/migrations and production identity wiring for both publisher transports. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "app.shipper.identity-projection.enforced=true",
        "app.auth.jwks-uri=http://localhost:8081/.well-known/jwks.json",
        "delivery.service.url=http://delivery-service", "app.internal.secret=identity-test-only"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TrackingIdentityPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @Autowired ShipperIdentityUseCase core;
    @Autowired ShipperIdentityResolver resolver;
    @Autowired ShipperIdentityProjectionRepository projections;
    @Autowired MeterRegistry metrics;
    @Autowired MockMvc mvc;
    @Autowired ShipperLocationWebSocketHandler socket;
    @MockitoBean TrackingPort locations;
    @MockitoBean ShipperPublisherSessionManager publishers;
    @MockitoBean ShipperPublisherLeaseRepository leases;
    @MockitoBean RedisGeoRepository geo;

    @Autowired ShipperIdentityProjectionListener identityEvents;
    @Autowired ShipperIdentityInboxReceiptRepository receipts;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void clean() { receipts.deleteAll(); projections.deleteAll(); }

    @Test void realListenerRetainsReplayStaleEqualVersionAndGapSemantics() throws Exception {
        UUID eventId = UUID.randomUUID();
        String initial = raw(eventId, 100L, 200L, 987L, 4);
        identityEvents.upsert(initial); identityEvents.upsert(initial);
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(projections.findById(100L).orElseThrow().getMappingVersion()).isEqualTo(4);
        assertThatThrownBy(() -> identityEvents.upsert(initial + " "))
                .isInstanceOf(IllegalStateException.class).hasMessage("Conflicting shipper identity event reuse");
        identityEvents.upsert(raw(UUID.randomUUID(), 100L, 201L, 988L, 3));
        assertThat(projections.findById(100L).orElseThrow().getShipperId()).isEqualTo(987);
        UUID gap = UUID.randomUUID();
        assertThatThrownBy(() -> identityEvents.upsert(raw(gap, 100L, 200L, 987L, 6)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Shipper identity mapping version gap");
        assertThat(receipts.existsById(gap)).isFalse();
        // Existing listener admits a different event at the same version; preserve this policy explicitly.
        identityEvents.upsert(raw(UUID.randomUUID(), 100L, 201L, 988L, 4));
        identityEvents.upsert(raw(UUID.randomUUID(), 100L, 201L, 988L, 5));
        assertThat(projections.findById(100L).orElseThrow().getMappingVersion()).isEqualTo(5);
        assertThat(receipts.count()).isEqualTo(4);
    }

    @Test void uniquenessFailureRollsBackProjectionAndReceiptTogether() throws Exception {
        mapping(100L, 200L, 987L);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> identityEvents.upsert(raw(id, 101L, 201L, 987L, 1)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(receipts.existsById(id)).isFalse();
        assertThat(projections.existsById(101L)).isFalse();
        assertThat(projections.findById(100L).orElseThrow().getShipperId()).isEqualTo(987);
        identityEvents.upsert(raw(id, 101L, 201L, 988L, 1));
        assertThat(receipts.existsById(id)).isTrue();
    }

    @Test void independentCoreInstancesConvergeOnConcurrentExactReplay() throws Exception {
        var first = new DefaultShipperIdentityInboxUseCase(newAdapter());
        var second = new DefaultShipperIdentityInboxUseCase(newAdapter());
        UUID id = UUID.randomUUID();
        String raw = raw(id, 100L, 200L, 987L, 1);
        var command = new ApplyShipperIdentityCommand(id, ShipperIdentityUpserted.TYPE, 100L, 200L, 987L, 1, raw);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                var instance = i % 2 == 0 ? first : second;
                results.add(executor.submit(() -> { start.await(); instance.apply(command); return null; }));
            }
            start.countDown();
            for (var result : results) result.get(15, TimeUnit.SECONDS);
            assertThat(receipts.count()).isEqualTo(1);
            assertThat(projections.count()).isEqualTo(1);
            assertThat(projections.findById(100L).orElseThrow().getMappingVersion()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void databaseLocksSerializeAbsentPrincipalAndGlobalEventIdentity() throws Exception {
        for (boolean sameEvent : List.of(false, true)) {
            UUID heldEvent = UUID.randomUUID();
            UUID nextEvent = sameEvent ? heldEvent : UUID.randomUUID();
            Long heldPrincipal = sameEvent ? 102L : 103L;
            Long nextPrincipal = sameEvent ? 104L : heldPrincipal;
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            var started = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
            var adapter = newAdapter();
            try {
                Future<?> owner = executor.submit(() -> adapter.atomically(heldEvent, heldPrincipal, () -> {
                    locked.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("lock release timeout"); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                }));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> contender = executor.submit(() -> {
                    started.countDown();
                    new DefaultShipperIdentityInboxUseCase(newAdapter()).apply(new ApplyShipperIdentityCommand(
                            nextEvent, ShipperIdentityUpserted.TYPE, nextPrincipal, nextPrincipal + 100,
                            nextPrincipal + 1000, 1, "locked event " + nextEvent));
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted", Long.class)).isPositive());
                assertThat(contender.isDone()).isFalse();
                assertThat(projections.existsById(nextPrincipal)).isFalse();
                release.countDown(); owner.get(10, TimeUnit.SECONDS); contender.get(10, TimeUnit.SECONDS);
                assertThat(receipts.existsById(nextEvent)).isTrue();
                assertThat(projections.existsById(nextPrincipal)).isTrue();
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    private JpaShipperIdentityInboxAdapter newAdapter() {
        return new JpaShipperIdentityInboxAdapter(projections, receipts, jdbc, transactions);
    }
    private String raw(UUID id, Long principal, Long legacy, Long shipper, long version) throws Exception {
        return mapper.writeValueAsString(new ShipperIdentityUpserted(id, ShipperIdentityUpserted.TYPE, 1,
                Instant.parse("2026-10-03T00:00:00Z"), UUID.randomUUID(), null,
                principal, legacy, shipper, version));
    }

    @Test void restAndSocketUseCanonicalShipperFromTheSameProductionCore() throws Exception {
        assertThat(core).isInstanceOf(DefaultShipperIdentityUseCase.class);
        mapping(100L, 200L, 987L);
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        when(locations.updateLocation(any())).thenAnswer(call -> {
            UpdateLocationCommand command = call.getArgument(0);
            return new LocationSnapshot(command.shipperId(), command.coordinate(), command.accuracy(),
                    command.speed(), command.heading(), command.online(), now, now);
        });
        mvc.perform(post("/api/tracking/shipper-locations/update").with(authentication(actor(100L, 200L)))
                .contentType("application/json").content("{\"latitude\":10.75,\"longitude\":106.65,\"isOnline\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shipperId").value(987));
        verify(locations).updateLocation(argThat(command -> command.shipperId() == 987));
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("identity-projection-session");
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("authenticatedPrincipalId", 100L,
                "authenticatedUserId", 200L, "authenticatedRole", "SHIPPER")));
        when(publishers.acquire(987L, "identity-projection-session"))
                .thenReturn(new PublisherLease(987L, "identity-projection-session", 1));
        socket.afterConnectionEstablished(session);
        verify(publishers).acquire(987L, "identity-projection-session");
        verify(session, never()).close(any());
        verify(session).sendMessage(any());
    }

    @Test void missingOrDivergentMappingFailsBeforeLocationWriteAndNeverCountsFallback() throws Exception {
        double before = fallbackCount();
        assertThatThrownBy(() -> resolver.requireShipperId(100L, 200L))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Shipper identity projection is not ready");
        mapping(100L, 201L, 987L);
        assertThatThrownBy(() -> resolver.requireShipperId(100L, 200L))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Shipper identity projection is divergent");
        // Existing global error translation remains unchanged by this extraction.
        mvc.perform(post("/api/tracking/shipper-locations/update").with(authentication(actor(100L, 200L)))
                .contentType("application/json").content("{\"latitude\":10.75,\"longitude\":106.65,\"isOnline\":true}"))
                .andExpect(status().isInternalServerError());
        verifyNoInteractions(locations);
        assertThat(fallbackCount()).isEqualTo(before);
    }

    @Test void onlyActualMissingProjectionFallbackIncrementsExistingRolloutCounter() {
        double before = fallbackCount();
        var preEnforcement = new ShipperIdentityResolver(core, false, metrics);
        assertThat(preEnforcement.requireShipperId(100L, 200L)).isEqualTo(200L);
        assertThat(fallbackCount()).isEqualTo(before + 1);
        mapping(100L, 200L, 987L);
        assertThat(preEnforcement.requireShipperId(100L, 200L)).isEqualTo(987L);
        assertThat(fallbackCount()).isEqualTo(before + 1);
        assertThatThrownBy(() -> preEnforcement.requireShipperId(100L, 201L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> preEnforcement.requireShipperId(null, 200L)).isInstanceOf(AccessDeniedException.class);
        assertThat(fallbackCount()).isEqualTo(before + 1);
    }

    private void mapping(long principal, long legacy, long shipper) {
        var row = new ShipperIdentityProjection(); row.setPrincipalId(principal); row.setLegacyUserId(legacy);
        row.setShipperId(shipper); row.setMappingVersion(1L); row.setUpdatedAt(LocalDateTime.now());
        projections.saveAndFlush(row);
    }
    private double fallbackCount() {
        return metrics.get("delivery.identity.legacy.fallback").tags("service", "tracking",
                "surface", "shipper_mapping_pre_enforcement").counter().count();
    }
    private AuthenticatedActorAuthenticationToken actor(long principal, long legacy) {
        var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject(Long.toString(legacy)).build();
        return new AuthenticatedActorAuthenticationToken(jwt,
                new AuthenticatedActor(principal, legacy, "fixture@example.test", Set.of("SHIPPER")),
                List.of(new SimpleGrantedAuthority("ROLE_SHIPPER")));
    }
}
