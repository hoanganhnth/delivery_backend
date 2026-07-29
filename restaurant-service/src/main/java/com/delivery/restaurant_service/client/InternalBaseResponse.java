package com.delivery.restaurant_service.client;

public record InternalBaseResponse<T>(int status, String message, T data) {
}
