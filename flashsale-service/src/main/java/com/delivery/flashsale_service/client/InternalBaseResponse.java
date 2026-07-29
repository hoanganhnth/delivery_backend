package com.delivery.flashsale_service.client;

public record InternalBaseResponse<T>(int status, String message, T data) {
}
