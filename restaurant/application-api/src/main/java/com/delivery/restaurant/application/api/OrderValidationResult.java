package com.delivery.restaurant.application.api;

import java.util.List;

public record OrderValidationResult(Boolean isValid,
        String message,
        Double calculatedTotal,
        List<OrderValidationError> errors,
        OrderValidationRestaurantInfo restaurantInfo,
        List<OrderValidationItemInfo> itemValidations) {

}
