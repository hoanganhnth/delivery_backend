package com.delivery.restaurant_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.restaurant.application.api.RestaurantUpdatePort;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.RestaurantCacheService;
import com.delivery.restaurant_service.service.SearchSyncPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2 proof for the extracted adapter; this is not PostgreSQL concurrency evidence. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:restaurant-update;DB_CLOSE_DELAY=-1",
        "app.search-sync.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestaurantUpdateIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RestaurantRepository restaurants;
    @Autowired RestaurantOutboxEventRepository outbox;
    @Autowired RestaurantUpdatePort updatePort;
    @Autowired MeterRegistry meterRegistry;
    @MockitoBean IdentityPrincipalClient identity;
    @MockitoBean RestaurantCacheService cache;
    @MockitoSpyBean SearchSyncPublisher search;

    @AfterEach
    void cleanTestDatabase() {
        outbox.deleteAll();
        restaurants.deleteAll();
    }

    @Test
    void updateLoadsDecidesFlushesOutboxAndCachesAfterCommit() throws Exception {
        assertThat(AopUtils.isAopProxy(updatePort)).isTrue();
        Restaurant restaurant = restaurant(101L, 7L);
        restaurant = restaurants.saveAndFlush(restaurant);
        doAnswer(invocation -> {
            Restaurant cached = invocation.getArgument(0);
            assertThat(restaurants.findById(cached.getId()).orElseThrow().getName())
                    .isEqualTo("Updated Restaurant");
            assertThat(outbox.count()).isEqualTo(1);
            return null;
        }).when(cache).cacheRestaurant(any());

        mvc.perform(put("/api/restaurants/{id}", restaurant.getId())
                        .with(actor(101L, 7L, "SHOP_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "name", "Updated Restaurant",
                                "openingHour", "10:00:00"))))
                .andExpect(status().isOk());

        Restaurant saved = restaurants.findById(restaurant.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Updated Restaurant");
        assertThat(saved.getOpeningHour()).isEqualTo(java.time.LocalTime.of(10, 0));
        assertThat(saved.getOwnerPrincipalId()).isEqualTo(101L);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getEventType()).isEqualTo("SEARCH_RESTAURANT_UPDATE");
        verify(cache).cacheRestaurant(any());
        verifyNoInteractions(identity);
    }

    @Test
    void adminCanUpdateAnotherOwnersRestaurantAndPersistsRepresentativeFields() throws Exception {
        Restaurant restaurant = restaurants.saveAndFlush(restaurant(101L, 7L));

        mvc.perform(put("/api/restaurants/{id}", restaurant.getId())
                        .with(actor(900L, 900L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Admin Updated Restaurant",
                                "address", "456 Admin Avenue",
                                "phone", "0987654321",
                                "openingHour", "10:00:00",
                                "closingHour", "22:00:00",
                                "defaultPrepTimeMinutes", 45,
                                "description", "Admin description",
                                "addressLat", 11.11,
                                "addressLng", 107.22,
                                "image", "admin.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(restaurant.getId()))
                .andExpect(jsonPath("$.data.version").value(restaurant.getVersion() + 1))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.rating").value(0.0))
                .andExpect(jsonPath("$.data.ratingCount").value(0))
                .andExpect(jsonPath("$.data.name").value("Admin Updated Restaurant"))
                .andExpect(jsonPath("$.data.address").value("456 Admin Avenue"))
                .andExpect(jsonPath("$.data.phone").value("0987654321"))
                .andExpect(jsonPath("$.data.openingHour").value("10:00:00"))
                .andExpect(jsonPath("$.data.closingHour").value("22:00:00"))
                .andExpect(jsonPath("$.data.defaultPrepTimeMinutes").value(45))
                .andExpect(jsonPath("$.data.description").value("Admin description"))
                .andExpect(jsonPath("$.data.latitude").value(11.11))
                .andExpect(jsonPath("$.data.longitude").value(107.22))
                .andExpect(jsonPath("$.data.image").value("admin.jpg"));

        Restaurant saved = restaurants.findById(restaurant.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Admin Updated Restaurant");
        assertThat(saved.getAddress()).isEqualTo("456 Admin Avenue");
        assertThat(saved.getPhone()).isEqualTo("0987654321");
        assertThat(saved.getOpeningHour()).isEqualTo(java.time.LocalTime.of(10, 0));
        assertThat(saved.getClosingHour()).isEqualTo(java.time.LocalTime.of(22, 0));
        assertThat(saved.getDefaultPrepTimeMinutes()).isEqualTo(45);
        assertThat(saved.getDescription()).isEqualTo("Admin description");
        assertThat(saved.getAddressLat()).isEqualTo(11.11);
        assertThat(saved.getAddressLng()).isEqualTo(107.22);
        assertThat(saved.getImage()).isEqualTo("admin.jpg");
        assertThat(saved.getOwnerPrincipalId()).isEqualTo(101L);
        assertThat(saved.getCreatorId()).isEqualTo(7L);
        assertThat(saved.getVersion()).isEqualTo(restaurant.getVersion() + 1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getEventType()).isEqualTo("SEARCH_RESTAURANT_UPDATE");
        verify(cache).cacheRestaurant(any());
        verifyNoInteractions(identity);
    }

    @Test
    void foreignShopOwnerCannotUpdateRestaurant() throws Exception {
        Restaurant restaurant = restaurants.saveAndFlush(restaurant(101L, 7L));

        mvc.perform(put("/api/restaurants/{id}", restaurant.getId())
                        .with(actor(202L, 8L, "SHOP_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Must Not Update\"}"))
                .andExpect(status().isForbidden());

        assertThat(restaurants.findById(restaurant.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(restaurant);
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(cache);
    }

    @Test
    void missingRestaurantReturnsNotFoundWithoutMutation() throws Exception {
        mvc.perform(put("/api/restaurants/{id}", 999999L)
                        .with(actor(101L, 7L, "SHOP_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.message").value("Restaurant not found"));

        assertThat(restaurants.count()).isZero();
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(cache);
    }

    @Test
    void incompleteMergedScheduleIsRejectedWithoutMutation() throws Exception {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Always Open");
        restaurant.setAddress("123 Main Street");
        restaurant.setCreatorId(7L);
        restaurant.setOwnerPrincipalId(101L);
        restaurant = restaurants.saveAndFlush(restaurant);

        mvc.perform(put("/api/restaurants/{id}", restaurant.getId())
                        .with(actor(101L, 7L, "SHOP_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Must Not Update\",\"openingHour\":\"10:00:00\"}"))
                .andExpect(status().isBadRequest());

        Restaurant saved = restaurants.findById(restaurant.getId()).orElseThrow();
        assertThat(saved.getOpeningHour()).isNull();
        assertThat(saved.getClosingHour()).isNull();
        assertThat(saved).usingRecursiveComparison().isEqualTo(restaurant);
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(cache);
    }

    @Test
    void legacyOwnerUpdateClaimsPrincipalAndEmitsManageFallbackMetric() throws Exception {
        Restaurant restaurant = restaurant(null, 7L);
        restaurant = restaurants.saveAndFlush(restaurant);

        mvc.perform(put("/api/restaurants/{id}", restaurant.getId())
                        .with(actor(101L, 7L, "SHOP_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Claimed Restaurant\"}"))
                .andExpect(status().isOk());

        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getOwnerPrincipalId())
                .isEqualTo(101L);
        assertThat(meterRegistry.get("delivery.identity.legacy.fallback")
                .tag("surface", "owner_manage").counter().count()).isEqualTo(1.0);
    }

    @Test
    void cacheFailureAfterCommitKeepsSuccessfulUpdateAndOutbox() throws Exception {
        Restaurant restaurant = restaurants.saveAndFlush(restaurant(101L, 7L));
        org.mockito.Mockito.doThrow(new IllegalStateException("redis unavailable"))
                .when(cache).cacheRestaurant(any());

        mvc.perform(put("/api/restaurants/{id}", restaurant.getId())
                        .with(actor(101L, 7L, "SHOP_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Committed Update\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Committed Update"));

        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getName())
                .isEqualTo("Committed Update");
        assertThat(outbox.count()).isEqualTo(1);
        verify(cache).cacheRestaurant(any());
    }

    @Test
    void searchFailureRollsBackUpdateOutboxAndAfterCommitCache() throws Exception {
        Restaurant restaurant = restaurants.saveAndFlush(restaurant(101L, 7L));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Injected update outbox failure");
        }).when(AopTestUtils.<SearchSyncPublisher>getUltimateTargetObject(search))
                .publishRestaurantChange(any(), eq("UPDATE"));

        mvc.perform(put("/api/restaurants/{id}", restaurant.getId())
                        .with(actor(101L, 7L, "SHOP_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Must Roll Back\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getName())
                .isEqualTo("Restaurant");
        assertThat(outbox).isNotNull();
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(cache);
    }

    private Restaurant restaurant(Long ownerPrincipalId, Long creatorId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Restaurant");
        restaurant.setAddress("123 Main Street");
        restaurant.setCreatorId(creatorId);
        restaurant.setOwnerPrincipalId(ownerPrincipalId);
        restaurant.setOpeningHour(java.time.LocalTime.of(9, 0));
        restaurant.setClosingHour(java.time.LocalTime.of(18, 0));
        return restaurant;
    }

    private RequestPostProcessor actor(Long principalId, Long legacyUserId, String role) {
        AuthenticatedActor actor = new AuthenticatedActor(
                principalId, legacyUserId, "owner@example.com", Set.of(role));
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256")
                .claim("sub", principalId.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        return authentication(new AuthenticatedActorAuthenticationToken(
                jwt, actor, Collections.emptyList()));
    }
}
