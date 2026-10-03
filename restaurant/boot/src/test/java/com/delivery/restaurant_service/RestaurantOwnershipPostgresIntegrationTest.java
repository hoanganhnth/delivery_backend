package com.delivery.restaurant_service;

import com.delivery.auth.resourceserver.security.*;
import com.delivery.restaurant.application.DefaultRestaurantOwnershipLookupUseCase;
import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true", "spring.kafka.listener.auto-startup=false",
        "app.outbox.relay-enabled=false", "app.search-sync.enabled=false", "order.service.url=http://order-service",
        "app.internal.secret=ownership-http-fixture", "app.identity.principal-ownership.enforced=false"
})
class RestaurantOwnershipPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired RestaurantOwnershipLookupUseCase ownership;
    @Autowired RestaurantRepository restaurants;
    @Autowired RestaurantOrderDecisionRepository decisions;
    @Autowired RestaurantOutboxEventRepository outbox;
    @Autowired MockMvc mvc;
    @Autowired MeterRegistry metrics;
    @MockitoBean OrderDecisionEligibilityPort orderEligibility;

    @Test void assignedPrincipalOwnerCanConfirmAndRejectWhileFormerCreatorHasNoAuthority() throws Exception {
        Long id = restaurant(100L, 200L);
        assertThat(ownership).isInstanceOf(DefaultRestaurantOwnershipLookupUseCase.class);
        String body = "{\"restaurantId\":" + id + ",\"estimatedPrepTime\":20}";
        mvc.perform(post("/api/restaurants/orders/99701/confirm").with(actor(200L, 200L)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        assertThat(decisions.findById(99701L)).isEmpty(); verifyNoInteractions(orderEligibility);
        mvc.perform(post("/api/restaurants/orders/99701/confirm").with(actor(100L, 999L)).contentType("application/json").content(body))
                .andExpect(status().isOk());
        assertThat(decisions.findById(99701L)).isPresent();
        var event = outbox.findAll().stream().filter(row -> row.getAggregateId().equals("99701")).findFirst().orElseThrow();
        assertThat(event.getPayload()).contains("\"actorUserId\":999");
        mvc.perform(post("/api/restaurants/orders/99702/reject").with(actor(100L, 999L)).contentType("application/json")
                .content("{\"restaurantId\":" + id + ",\"reason\":\"Closed\"}"))
                .andExpect(status().isOk());
        assertThat(decisions.findById(99702L)).isPresent();
    }
    @Test void internalLookupPreservesOldClientContractAndCountsOnlyRealUnmigratedFallback() throws Exception {
        Long migrated = restaurant(100L, 200L); Long legacy = restaurant(null, 200L); double before = fallbackCount();
        String path = "/api/restaurants/internal/";
        mvc.perform(get(path + migrated + "/owners/200")).andExpect(status().isForbidden());
        mvc.perform(get(path + migrated + "/owners/200").header("Internal-Token", "ownership-http-fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(get(path + migrated + "/owners/100").param("legacyOwnerId", "999").header("Internal-Token", "ownership-http-fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(get(path + migrated + "/owners/101").param("legacyOwnerId", "200").header("Internal-Token", "ownership-http-fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(false));
        assertThat(fallbackCount()).isEqualTo(before);
        mvc.perform(get(path + legacy + "/owners/100").param("legacyOwnerId", "200").header("Internal-Token", "ownership-http-fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        assertThat(fallbackCount()).isEqualTo(before + 1);
        assertThat(ownership.internalCheck(legacy, 100L, 200L, true).owned()).isFalse();
    }
    @Test void enforcementDisablesDecisionFallbackAndMissingRestaurantFailsClosed() {
        Long id = restaurant(null, 200L);
        assertThat(ownership.canDecideOrder(id, RestaurantActorRole.SHOP_OWNER, 100L, 200L, false)).isTrue();
        assertThat(ownership.canDecideOrder(id, RestaurantActorRole.SHOP_OWNER, 100L, 200L, true)).isFalse();
        assertThat(ownership.canDecideOrder(Long.MAX_VALUE, RestaurantActorRole.SHOP_OWNER, 100L, 200L, false)).isFalse();
        assertThat(ownership.internalCheck(Long.MAX_VALUE, 100L, 200L, false).owned()).isFalse();
    }
    private Long restaurant(Long owner, Long creator) {
        var row = new Restaurant(); row.setName("Ownership fixture " + UUID.randomUUID()); row.setOwnerPrincipalId(owner); row.setCreatorId(creator);
        return restaurants.saveAndFlush(row).getId();
    }
    private double fallbackCount() {
        var counter = metrics.find("delivery.identity.legacy.fallback").tags("service", "restaurant", "surface", "internal_owner_check").counter();
        return counter == null ? 0 : counter.count();
    }
    private RequestPostProcessor actor(Long principal, Long legacy) {
        var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject(legacy.toString()).build();
        return authentication(new AuthenticatedActorAuthenticationToken(jwt,
                new AuthenticatedActor(principal, legacy, "fixture@example.test", Set.of("SHOP_OWNER")), List.of(new SimpleGrantedAuthority("ROLE_SHOP_OWNER"))));
    }
}
