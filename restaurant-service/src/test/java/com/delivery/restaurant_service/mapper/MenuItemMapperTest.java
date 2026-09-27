package com.delivery.restaurant_service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.restaurant.application.api.MenuItemCreateResult;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.MenuItemUpdateResult;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void applicationSnapshotsAndMutationResultsMapToTheHostResponse() {
        MenuItemSnapshot snapshot = snapshot();
        MenuItemMapper mapper = new MenuItemMapper();

        assertThat(mapper.toResponse(snapshot).getStatus()).isEqualTo("SOLD_OUT");
        assertThat(mapper.toResponse(new MenuItemCreateResult(snapshot, false)).getImage())
                .isEqualTo("dish.jpg");
        assertThat(mapper.toResponse(new MenuItemUpdateResult(snapshot, true)).getVersion())
                .isEqualTo(4L);
    }

    @Test
    void applicationPageSlicesMapToSpringPagesWithMetadata() {
        MenuItemMapper mapper = new MenuItemMapper();
        MenuItemPageSlice source = new MenuItemPageSlice(
                List.of(snapshot()), 2, 10, 21, 3, false);

        var page = mapper.toPage(source);

        assertThat(page.getContent()).extracting(MenuItemResponse::getName)
                .containsExactly("Dish");
        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(21);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
    }

    private MenuItemSnapshot snapshot() {
        return new MenuItemSnapshot(4L, 9L, "Dish", "Description", new BigDecimal("12.50"),
                MenuItemStatus.SOLD_OUT, LocalDateTime.MIN, LocalDateTime.MAX, "dish.jpg", 4L);
    }
}
