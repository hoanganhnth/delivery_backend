package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.entity.Restaurant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantMapperTest {

    @Test
    void restaurantImageIsPreservedOnCreateAndUpdate() {
        RestaurantMapper mapper = new RestaurantMapper();
        CreateRestaurantRequest create = new CreateRestaurantRequest();
        create.setName("Bếp MVP");
        create.setImage("create.jpg");

        Restaurant restaurant = mapper.toEntity(create);
        assertThat(restaurant.getImage()).isEqualTo("create.jpg");

        UpdateRestaurantRequest update = new UpdateRestaurantRequest();
        update.setImage("updated.jpg");
        mapper.updateEntityFromDto(update, restaurant);

        assertThat(restaurant.getImage()).isEqualTo("updated.jpg");
        assertThat(mapper.toResponse(restaurant).getImage()).isEqualTo("updated.jpg");
    }

    @Test
    void menuItemImageIsPreserved() {
        MenuItemMapper mapper = new MenuItemMapper();
        CreateMenuItemRequest create = new CreateMenuItemRequest();
        create.setName("Món MVP");
        create.setImage("dish.jpg");

        assertThat(mapper.toEntity(create).getImage()).isEqualTo("dish.jpg");
    }
}
