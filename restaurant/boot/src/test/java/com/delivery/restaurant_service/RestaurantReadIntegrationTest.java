package com.delivery.restaurant_service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:restaurant-read;DB_CLOSE_DELAY=-1",
        "app.identity.principal-ownership.enforced=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestaurantReadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired RestaurantRepository restaurants;
    @MockitoBean IdentityPrincipalClient identity;

    @Test
    void publicListSearchAndPageHideArchivedRestaurants() throws Exception {
        Restaurant visible = save("Visible Noodle", 101L, 7L, RestaurantStatus.ACTIVE);
        save("Hidden Noodle", 101L, 7L, RestaurantStatus.ARCHIVED);

        mvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(visible.getId()))
                .andExpect(jsonPath("$.data[0].name").value("Visible Noodle"));

        mvc.perform(get("/api/restaurants/search").param("keyword", "Noodle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Visible Noodle"));

        mvc.perform(get("/api/restaurants/page")
                        .param("page", "0")
                        .param("size", "10")
                        .param("keyword", "Noodle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Visible Noodle"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void archivedDetailRemainsAvailableForHistory() throws Exception {
        Restaurant archived = save("Archived History", 101L, 7L, RestaurantStatus.ARCHIVED);

        mvc.perform(get("/api/restaurants/{id}", archived.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(archived.getId()))
                .andExpect(jsonPath("$.data.name").value("Archived History"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ARCHIVED"));
    }

    @Test
    void adminManagementIncludesArchivedRestaurants() throws Exception {
        Restaurant active = save("Active Management", 101L, 7L, RestaurantStatus.ACTIVE);
        Restaurant archived = save("Archived Management", 202L, 8L, RestaurantStatus.ARCHIVED);

        JsonNode data = responseData(mvc.perform(get("/api/restaurants/my-restaurants")
                .with(actor(900L, 900L, "ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(data).hasSize(2);
        assertThat(List.of(data.get(0).get("id").asLong(), data.get(1).get("id").asLong()))
                .containsExactlyInAnyOrder(active.getId(), archived.getId());
    }

    @Test
    void shopOwnerManagementUsesPrincipalFilteringWhenEnforced() throws Exception {
        Restaurant owned = save("Owned", 101L, 7L, RestaurantStatus.ACTIVE);
        save("Foreign Principal", 202L, 7L, RestaurantStatus.ACTIVE);
        save("Legacy Same Creator", null, 7L, RestaurantStatus.ACTIVE);

        JsonNode data = responseData(mvc.perform(get("/api/restaurants/my-restaurants")
                .with(actor(101L, 7L, "SHOP_OWNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("id").asLong()).isEqualTo(owned.getId());
    }

    private Restaurant save(String name, Long ownerPrincipalId, Long creatorId, RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setAddress("123 Main Street");
        restaurant.setOwnerPrincipalId(ownerPrincipalId);
        restaurant.setCreatorId(creatorId);
        restaurant.setLifecycleStatus(status);
        return restaurants.saveAndFlush(restaurant);
    }

    private RequestPostProcessor actor(Long principalId, Long legacyUserId, String role) {
        AuthenticatedActor actor = new AuthenticatedActor(
                principalId, legacyUserId, "actor@example.com", Set.of(role));
        Jwt jwt = Jwt.withTokenValue("read-test-token").header("alg", "RS256")
                .claim("sub", principalId.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        return authentication(new AuthenticatedActorAuthenticationToken(
                jwt, actor, Collections.emptyList()));
    }

    private JsonNode responseData(String response) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("data");
    }
}
