package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant.domain.catalog.OperatingSchedule;
import com.delivery.restaurant.application.api.CreateRestaurantResult;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;

@Component
public class RestaurantMapper {
    private final Clock clock;

    public RestaurantMapper() {
        this(Clock.systemUTC());
    }

    RestaurantMapper(Clock clock) {
        this.clock = clock;
    }

    public RestaurantResponse toResponse(Restaurant restaurant) {
        if (restaurant == null) {
            return null;
        }
        RestaurantResponse response = new RestaurantResponse();
        response.setId(restaurant.getId());
        response.setName(restaurant.getName());
        response.setAddress(restaurant.getAddress());
        response.setPhone(restaurant.getPhone());
        response.setOpeningHour(restaurant.getOpeningHour());
        response.setClosingHour(restaurant.getClosingHour());
        response.setDefaultPrepTimeMinutes(restaurant.getDefaultPrepTimeMinutes());
        response.setImage(restaurant.getImage());
        response.setDescription(restaurant.getDescription());
        response.setLatitude(restaurant.getAddressLat());
        response.setLongitude(restaurant.getAddressLng());
        response.setRating(restaurant.getRating());
        response.setRatingCount(restaurant.getRatingCount());
        response.setLifecycleStatus(restaurant.getLifecycleStatus());
        response.setVersion(restaurant.getVersion());
        response.setTimeZone(restaurant.getTimeZone());
        response.setOpen(isRestaurantOpen(restaurant));
        return response;
    }

    private boolean isRestaurantOpen(Restaurant restaurant) {
        return isRestaurantOpen(restaurant.getOpeningHour(), restaurant.getClosingHour(), restaurant.getTimeZone());
    }

    public RestaurantResponse toResponse(CreateRestaurantResult result) {
        RestaurantResponse response = new RestaurantResponse();
        response.setId(result.id());
        response.setName(result.name());
        response.setAddress(result.address());
        response.setPhone(result.phone());
        response.setOpeningHour(result.openingHour());
        response.setClosingHour(result.closingHour());
        response.setDefaultPrepTimeMinutes(result.defaultPrepTimeMinutes());
        response.setImage(result.image());
        response.setDescription(result.description());
        response.setLatitude(result.latitude());
        response.setLongitude(result.longitude());
        response.setRating(result.rating());
        response.setRatingCount(result.ratingCount());
        response.setLifecycleStatus(result.lifecycleStatus());
        response.setVersion(result.version());
        response.setTimeZone(result.timeZone());
        response.setOpen(isRestaurantOpen(result.openingHour(), result.closingHour(), result.timeZone()));
        return response;
    }

    public RestaurantResponse toResponse(RestaurantSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        RestaurantResponse response = new RestaurantResponse();
        response.setId(snapshot.id());
        response.setName(snapshot.name());
        response.setAddress(snapshot.address());
        response.setPhone(snapshot.phone());
        response.setOpeningHour(snapshot.openingHour());
        response.setClosingHour(snapshot.closingHour());
        response.setDefaultPrepTimeMinutes(snapshot.defaultPrepTimeMinutes());
        response.setImage(snapshot.image());
        response.setDescription(snapshot.description());
        response.setLatitude(snapshot.latitude());
        response.setLongitude(snapshot.longitude());
        response.setRating(snapshot.rating());
        response.setRatingCount(snapshot.ratingCount());
        response.setLifecycleStatus(snapshot.lifecycleStatus());
        response.setVersion(snapshot.version());
        response.setTimeZone(snapshot.timeZone());
        response.setOpen(isRestaurantOpen(snapshot.openingHour(), snapshot.closingHour(), snapshot.timeZone()));
        return response;
    }

    private boolean isRestaurantOpen(java.time.LocalTime openingHour, java.time.LocalTime closingHour,
            String timeZone) {
        try {
            return OperatingSchedule.of(openingHour, closingHour, ZoneId.of(timeZone)).isOpenAt(clock.instant());
        } catch (RuntimeException invalidSchedule) {
            return false;
        }
    }
}
