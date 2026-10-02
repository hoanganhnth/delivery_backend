package com.delivery.restaurant_service;

import com.delivery.auth.resourceserver.security.*;
import com.delivery.restaurant_service.entity.*;
import com.delivery.restaurant_service.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MenuHttpOwnershipTest {
    @Autowired MockMvc mvc;
    @Autowired RestaurantRepository restaurants;
    @Autowired MenuItemRepository items;
    private RequestPostProcessor actor(long principal, String role) {
        var jwt = Jwt.withTokenValue("test-only").header("alg", "RS256").subject("" + principal).build();
        return authentication(new AuthenticatedActorAuthenticationToken(jwt,
                new AuthenticatedActor(principal, 7L, "test@example.com", Set.of(role)),
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    private MenuItem seed() {
        var restaurant = new Restaurant(); restaurant.setName("Foreign restaurant");
        restaurant.setCreatorId(7L); restaurant.setOwnerPrincipalId(202L);
        restaurants.saveAndFlush(restaurant);
        var item = new MenuItem(); item.setRestaurant(restaurant); item.setName("Sold out");
        item.setPrice(BigDecimal.valueOf(50000)); item.setStatus(MenuItem.Status.SOLD_OUT);
        return items.saveAndFlush(item);
    }
    @Test void ownerCannotReadForeignMenuOnEitherManagementRoute() throws Exception {
        var item = seed();
        for (var suffix : List.of("", "/page")) {
            mvc.perform(get("/api/menu-items/my-menu-items" + suffix)
                    .param("restaurantId", "" + item.getRestaurant().getId()).with(actor(101, "SHOP_OWNER")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/menu-items/my-menu-items").with(actor(101, "SHOP_OWNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/menu-items/my-menu-items").with(actor(202, "SHOP_OWNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("SOLD_OUT"));
        mvc.perform(get("/api/menu-items/my-menu-items").with(actor(999, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(item.getId()));
    }
    @Test void publicCatalogueIsAvailableOnlyIncludingAnonymousPages() throws Exception {
        var item = seed();
        String base = "/api/menu-items/restaurant/" + item.getRestaurant().getId();
        for (var suffix : List.of("", "/available", "/page", "/available/page")) {
            var dataPath = suffix.endsWith("page") ? "$.data.items" : "$.data";
            mvc.perform(get(base + suffix)).andExpect(status().isOk()).andExpect(jsonPath(dataPath).isEmpty());
        }
    }
    @Test void anonymousAndUserCannotManage() throws Exception {
        mvc.perform(get("/api/menu-items/my-menu-items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/menu-items/my-menu-items").with(actor(202, "USER")))
                .andExpect(status().isForbidden());
    }
}
