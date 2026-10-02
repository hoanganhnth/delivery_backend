package com.delivery.restaurant.application.api;

/** Persists validated creation, Search outbox and after-commit cache work atomically. */
public interface RestaurantCreationPort {
    CreateRestaurantResult create(CreateRestaurantCommand command, long ownerPrincipalId);
}
