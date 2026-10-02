package com.delivery.restaurant.application.api;

import java.util.List;

/** Only canonical identities and requested quantities enter checkout validation. */
public record OrderValidationCommand(Long restaurantId, Double deliveryLat, Double deliveryLng,
        List<OrderValidationLineCommand> items) { }
