package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
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

    public Restaurant toEntity(CreateRestaurantRequest request) {
        if (request == null) {
            return null;
        }
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.getName());
        restaurant.setDescription(request.getDescription());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone());
        restaurant.setOpeningHour(request.getOpeningHour());
        restaurant.setClosingHour(request.getClosingHour());
        if (request.getDefaultPrepTimeMinutes() != null) {
            restaurant.setDefaultPrepTimeMinutes(request.getDefaultPrepTimeMinutes());
        }
        restaurant.setImage(request.getImage());
        restaurant.setAddressLat(request.getAddressLat());
        restaurant.setAddressLng(request.getAddressLng());
        return restaurant;
    }

    public void updateEntityFromDto(UpdateRestaurantRequest request, Restaurant restaurant) {
        if (request == null || restaurant == null) {
            return;
        }
        if (request.getName() != null) restaurant.setName(request.getName());
        if (request.getDescription() != null) restaurant.setDescription(request.getDescription());
        if (request.getAddress() != null) restaurant.setAddress(request.getAddress());
        if (request.getPhone() != null) restaurant.setPhone(request.getPhone());
        if (request.getOpeningHour() != null) restaurant.setOpeningHour(request.getOpeningHour());
        if (request.getClosingHour() != null) restaurant.setClosingHour(request.getClosingHour());
        if (request.getDefaultPrepTimeMinutes() != null) {
            restaurant.setDefaultPrepTimeMinutes(request.getDefaultPrepTimeMinutes());
        }
        if (request.getImage() != null) restaurant.setImage(request.getImage());
        if (request.getAddressLat() != null) restaurant.setAddressLat(request.getAddressLat());
        if (request.getAddressLng() != null) restaurant.setAddressLng(request.getAddressLng());
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
