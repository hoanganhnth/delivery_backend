package com.delivery.restaurant_service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:restaurant-read-legacy;DB_CLOSE_DELAY=-1",
        "app.identity.principal-ownership.enforced=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestaurantReadLegacyFallbackIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RestaurantRepository restaurants;
    @Autowired MeterRegistry meterRegistry;
    @MockitoBean IdentityPrincipalClient identity;

    @Test
    void shopOwnerReadIncludesUnmigratedLegacyRowsAndEmitsFallbackMetric() throws Exception {
        Restaurant principalOwned = save("Principal Owned", 101L, 8L);
        Restaurant legacyOwned = save("Legacy Owned", null, 7L);
        save("Other Legacy", null, 8L);
        save("Foreign Principal", 202L, 7L);
        double before = fallbackCount();

        String response = mvc.perform(get("/api/restaurants/my-restaurants")
                        .with(actor(101L, 7L, "SHOP_OWNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = json.readTree(response).get("data");

        assertThat(data).hasSize(2);
        assertThat(java.util.stream.StreamSupport.stream(data.spliterator(), false)
                .map(node -> node.get("id").asLong()).toList())
                .containsExactlyInAnyOrder(principalOwned.getId(), legacyOwned.getId());
        assertThat(fallbackCount()).isEqualTo(before + 1);
    }

    private double fallbackCount() {
        var counter = meterRegistry.find("delivery.identity.legacy.fallback")
                .tag("service", "restaurant").tag("surface", "owner_list").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private Restaurant save(String name, Long ownerPrincipalId, Long creatorId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setAddress("123 Main Street");
        restaurant.setOwnerPrincipalId(ownerPrincipalId);
        restaurant.setCreatorId(creatorId);
        restaurant.setLifecycleStatus(RestaurantStatus.ACTIVE);
        return restaurants.saveAndFlush(restaurant);
    }

    private RequestPostProcessor actor(Long principalId, Long legacyUserId, String role) {
        AuthenticatedActor actor = new AuthenticatedActor(
                principalId, legacyUserId, "actor@example.com", Set.of(role));
        Jwt jwt = Jwt.withTokenValue("legacy-read-test-token").header("alg", "RS256")
                .claim("sub", principalId.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        return authentication(new AuthenticatedActorAuthenticationToken(
                jwt, actor, Collections.emptyList()));
    }
}
