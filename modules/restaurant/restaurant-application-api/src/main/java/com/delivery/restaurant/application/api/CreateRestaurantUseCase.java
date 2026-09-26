package com.delivery.restaurant.application.api;

public interface CreateRestaurantUseCase {
    CreateRestaurantResult create(CreateRestaurantCommand command);
}
