package com.delivery.restaurant.application.api;

public interface OrderValidationUseCase {
    OrderValidationResult validate(OrderValidationCommand command);
}
