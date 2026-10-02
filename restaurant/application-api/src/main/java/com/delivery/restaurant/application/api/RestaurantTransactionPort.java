package com.delivery.restaurant.application.api;

import java.util.function.Supplier;

public interface RestaurantTransactionPort {
    <T> T required(Supplier<T> operation);
}
