package com.delivery.tracking_service.service;

import com.delivery.tracking.application.DefaultShipperIdentityUseCase;
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

    @BeforeEach void clean() { projections.deleteAll(); }

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
