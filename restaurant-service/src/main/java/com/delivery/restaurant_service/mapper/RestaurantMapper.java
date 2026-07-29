package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
@Component
public class RestaurantMapper {
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
        response.setImage(restaurant.getImage());
        response.setDescription(restaurant.getDescription());
        response.setLatitude(restaurant.getAddressLat());
        response.setLongitude(restaurant.getAddressLng());
        response.setRating(restaurant.getRating());
        response.setRatingCount(restaurant.getRatingCount());
        response.setOpen(isRestaurantOpen(restaurant.getOpeningHour(), restaurant.getClosingHour()));
        return response;
    }

    public boolean isRestaurantOpen(LocalTime opening, LocalTime closing) {
        if (opening == null || closing == null) {
            return true;
        }
        LocalTime now = LocalTime.now();
        return now.isAfter(opening) && now.isBefore(closing);
    }
}
