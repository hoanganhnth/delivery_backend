package com.delivery.restaurant_service;

import com.delivery.restaurant_service.entity.*;
import com.delivery.restaurant_service.repository.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.restaurant.inventory-enabled=true", "app.restaurant.inventory-consumer-enabled=false",
        "app.internal.secret=inventory-http-fixture"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InternalInventoryHttpSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired RestaurantRepository restaurants;
    @Autowired MenuItemRepository items;
    @Autowired MenuItemInventoryRepository stocks;
    @Autowired MenuItemInventoryReservationRepository reservations;
    static final String BASE = "/api/menu-items/internal/inventory/reservations";

    @Test void internalCredentialWithoutBearerCanReserveCommitAndCompensate() throws Exception {
        var f = fixture(); UUID id = UUID.randomUUID();
        mvc.perform(post(BASE).contentType("application/json").header("Internal-Token", "inventory-http-fixture").content(payload(f, id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("RESERVED"));
        assertThat(stocks.findById(f.menu()).orElseThrow().getReservedQuantity()).isEqualTo(2);
        mvc.perform(post(BASE + "/" + id + "/commit").param("orderId", "99601").header("Internal-Token", "inventory-http-fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("COMMITTED"));
        assertThat(stocks.findById(f.menu()).orElseThrow().getOnHandQuantity()).isEqualTo(3);
        mvc.perform(post(BASE + "/" + id + "/release").param("orderId", "99601").header("Internal-Token", "inventory-http-fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("RELEASED"));
        assertThat(stocks.findById(f.menu()).orElseThrow().getOnHandQuantity()).isEqualTo(5);
    }
    @Test void missingOrWrongCredentialCannotMutateEvenOnAllowedPrivateRoutes() throws Exception {
        var f = fixture(); UUID id = UUID.randomUUID();
        for (String token : new String[] {null, "wrong"}) {
            var reserve = post(BASE).contentType("application/json").content(payload(f, id));
            if (token != null) reserve.header("Internal-Token", token);
            mvc.perform(reserve).andExpect(status().isForbidden());
            for (String transition : new String[] {"commit", "release"}) {
                var command = post(BASE + "/" + id + "/" + transition).param("orderId", "99601");
                if (token != null) command.header("Internal-Token", token);
                mvc.perform(command).andExpect(status().isForbidden());
            }
        }
        assertThat(reservations.findById(id)).isEmpty();
        assertThat(stocks.findById(f.menu()).orElseThrow().getReservedQuantity()).isZero();
    }
    @Test void internalCredentialDoesNotAuthorizeOtherMethodsRoutesOrManagement() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get(BASE).header("Internal-Token", "inventory-http-fixture")).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE).header("Internal-Token", "inventory-http-fixture")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/" + id + "/commit/extra").header("Internal-Token", "inventory-http-fixture")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/menu-items/internal/other").header("Internal-Token", "inventory-http-fixture")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/menu-items/1/inventory").header("Internal-Token", "inventory-http-fixture").contentType("application/json").content("{\"onHandQuantity\":5}"))
                .andExpect(status().isUnauthorized());
    }
    private Fixture fixture() {
        var restaurant = new Restaurant(); restaurant.setName("Internal route fixture"); restaurant.setCreatorId(200L); restaurant = restaurants.saveAndFlush(restaurant);
        var item = new MenuItem(); item.setName("Meal"); item.setPrice(BigDecimal.TEN); item.setRestaurant(restaurant); item.setStatus(MenuItem.Status.AVAILABLE); item = items.saveAndFlush(item);
        var stock = new MenuItemInventory(); stock.setMenuItemId(item.getId()); stock.setOnHandQuantity(5); stocks.saveAndFlush(stock);
        return new Fixture(restaurant.getId(), item.getId());
    }
    private String payload(Fixture f, UUID id) {
        return "{\"reservationId\":\"" + id + "\",\"orderId\":99601,\"restaurantId\":" + f.restaurant()
                + ",\"items\":[{\"menuItemId\":" + f.menu() + ",\"quantity\":2}]}";
    }
    private record Fixture(Long restaurant, Long menu) { }
}
