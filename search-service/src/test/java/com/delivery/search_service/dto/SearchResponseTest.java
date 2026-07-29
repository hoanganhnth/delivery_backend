package com.delivery.search_service.dto;

import com.delivery.search_service.document.DishDocument;
import com.delivery.search_service.document.RestaurantDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResponseTest {

    @Test
    void mapsIndexDocumentsToStableHttpDtos() {
        RestaurantSearchResponse restaurant = RestaurantSearchResponse.from(
                RestaurantDocument.builder().id("11").name("Phở").rating(4.5).build());
        DishSearchResponse dish = DishSearchResponse.from(
                DishDocument.builder().id("21").name("Phở bò").price(new BigDecimal("55000"))
                        .restaurantId("11").build());

        assertThat(restaurant.id()).isEqualTo("11");
        assertThat(restaurant.rating()).isEqualTo(4.5);
        assertThat(dish.price()).isEqualByComparingTo("55000");
        assertThat(dish.restaurantId()).isEqualTo("11");
    }
}
