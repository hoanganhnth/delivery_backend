package com.delivery.restaurant_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class CatalogArchiveIntegrationTest {

    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MenuItemRepository menuItemRepository;

    @Test
    void archivingRestaurantPreservesMenuRowsAndTheirSellingState() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("History Restaurant");
        restaurant.setCreatorId(1L);
        restaurant = restaurantRepository.saveAndFlush(restaurant);
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setName("Historical Dish");
        item.setPrice(BigDecimal.TEN);
        item.setStatus(MenuItem.Status.AVAILABLE);
        item = menuItemRepository.saveAndFlush(item);

        restaurant.setLifecycleStatus(RestaurantStatus.ARCHIVED);
        restaurantRepository.saveAndFlush(restaurant);

        assertThat(restaurantRepository.findById(restaurant.getId()).orElseThrow()
                .getLifecycleStatus()).isEqualTo(RestaurantStatus.ARCHIVED);
        assertThat(menuItemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(MenuItem.Status.AVAILABLE);
    }
}
