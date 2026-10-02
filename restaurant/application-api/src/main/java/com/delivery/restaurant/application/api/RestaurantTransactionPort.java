package com.delivery.restaurant.application.api;

import java.util.function.Supplier;

public interface RestaurantTransactionPort {
    <T> T repeatableRead(Supplier<T> operation);
    <T> T readOnly(Supplier<T> operation);
    <T> T required(Supplier<T> operation);
}
