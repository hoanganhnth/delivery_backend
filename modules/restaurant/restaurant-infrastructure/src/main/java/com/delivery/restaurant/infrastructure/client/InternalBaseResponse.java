package com.delivery.restaurant.infrastructure.client;

/** Wire envelope returned by Order's internal validation endpoints. */
public record InternalBaseResponse<T>(int status, String message, T data) {
}
