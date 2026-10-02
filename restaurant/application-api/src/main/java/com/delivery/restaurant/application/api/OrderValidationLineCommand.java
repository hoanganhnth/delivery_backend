package com.delivery.restaurant.application.api;

public record OrderValidationLineCommand(Long menuItemId, Integer quantity) { }
