package com.delivery.restaurant_service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import org.junit.jupiter.api.Test;

class MenuItemMapperTest {

    @Test
    void updateCannotReassignMenuItemToAnotherRestaurant() {
        Restaurant ownerRestaurant = new Restaurant();
        ownerRestaurant.setId(1L);
        MenuItem item = new MenuItem();
        item.setRestaurant(ownerRestaurant);
        item.setName("Dish");

        UpdateMenuItemRequest request = new UpdateMenuItemRequest();
        request.setRestaurantId(2L);
        request.setName("Renamed Dish");

        new MenuItemMapper().updateEntityFromDto(request, item);

        assertThat(item.getName()).isEqualTo("Renamed Dish");
        assertThat(item.getRestaurant()).isSameAs(ownerRestaurant);
        assertThat(item.getRestaurant().getId()).isNotEqualTo(request.getRestaurantId());
    }
}
