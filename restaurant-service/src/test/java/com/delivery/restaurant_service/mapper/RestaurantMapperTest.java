package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.application.api.CreateRestaurantResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantMapperTest {

    @Test
    void creationSnapshotMapsEveryResponseFieldAndUsesTheSameScheduleRules() {
        RestaurantMapper mapper = new RestaurantMapper(Clock.fixed(
                Instant.parse("2026-09-26T17:30:00Z"), ZoneOffset.UTC));
        var result = new CreateRestaurantResult(91L, "Bếp MVP", "123 Main Street", "0123456789",
                LocalTime.of(18, 0), LocalTime.of(2, 0), 45, "create.jpg", "Description",
                10.78, 106.69, 0.0, 0, RestaurantStatus.ACTIVE, 0L, "Asia/Ho_Chi_Minh");

        var response = mapper.toResponse(result);

        assertThat(response).usingRecursiveComparison().ignoringFields("open").isEqualTo(result);
        assertThat(response.isOpen()).isTrue();
        var closingBoundary = new RestaurantMapper(Clock.fixed(
                Instant.parse("2026-09-26T19:00:00Z"), ZoneOffset.UTC));
        assertThat(closingBoundary.toResponse(result).isOpen()).isFalse();
    }

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
    void catalogResponsesExposeLifecycleVersionAndTimezone() {
        RestaurantMapper restaurantMapper = new RestaurantMapper();
        Restaurant restaurant = new Restaurant();
        restaurant.setLifecycleStatus(RestaurantStatus.PAUSED);
        restaurant.setVersion(7L);
        restaurant.setTimeZone("Asia/Bangkok");

        assertThat(restaurantMapper.toResponse(restaurant).getLifecycleStatus())
                .isEqualTo(RestaurantStatus.PAUSED);
        assertThat(restaurantMapper.toResponse(restaurant).getVersion()).isEqualTo(7L);
        assertThat(restaurantMapper.toResponse(restaurant).getTimeZone()).isEqualTo("Asia/Bangkok");

        MenuItem item = new MenuItem();
        item.setVersion(3L);
        assertThat(new MenuItemMapper().toResponse(item).getVersion()).isEqualTo(3L);
    }

    @Test
    void responseAvailabilityUsesRestaurantTimezoneAndSupportsOvernightHours() {
        Restaurant restaurant = new Restaurant();
        restaurant.setOpeningHour(LocalTime.of(18, 0));
        restaurant.setClosingHour(LocalTime.of(2, 0));
        restaurant.setTimeZone("Asia/Ho_Chi_Minh");
        RestaurantMapper mapper = new RestaurantMapper(Clock.fixed(
                Instant.parse("2026-09-26T17:30:00Z"), ZoneOffset.UTC));

        assertThat(mapper.toResponse(restaurant).isOpen()).isTrue();
    }

    @Test
    void responseAvailabilityExcludesClosingBoundaryInRestaurantTimezone() {
        Restaurant restaurant = new Restaurant();
        restaurant.setOpeningHour(LocalTime.of(18, 0));
        restaurant.setClosingHour(LocalTime.of(2, 0));
        restaurant.setTimeZone("Asia/Ho_Chi_Minh");
        RestaurantMapper mapper = new RestaurantMapper(Clock.fixed(
                Instant.parse("2026-09-26T19:00:00Z"), ZoneOffset.UTC));

        assertThat(mapper.toResponse(restaurant).isOpen()).isFalse();
    }

    @Test
    void responseAvailabilityFailsClosedForIncompleteStoredSchedule() {
        Restaurant restaurant = new Restaurant();
        restaurant.setOpeningHour(LocalTime.of(18, 0));
        restaurant.setTimeZone("Asia/Ho_Chi_Minh");
        RestaurantMapper mapper = new RestaurantMapper(Clock.fixed(
                Instant.parse("2026-09-26T17:30:00Z"), ZoneOffset.UTC));

        assertThat(mapper.toResponse(restaurant).isOpen()).isFalse();
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
