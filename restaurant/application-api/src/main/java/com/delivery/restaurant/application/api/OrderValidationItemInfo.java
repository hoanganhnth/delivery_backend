package com.delivery.restaurant.application.api;

public record OrderValidationItemInfo(Long menuItemId,
            String menuItemName,
            Boolean isAvailable,
            Double actualPrice,
            Double expectedPrice,
            Boolean priceMatches,
            Integer requestedQuantity,
            Integer availableStock,
            Boolean hasEnoughStock) { }
