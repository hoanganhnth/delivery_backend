package com.delivery.restaurant.application.api;

public record OrderValidationError(String field,
            String errorCode,
            String message,
            Object invalidValue) { }
