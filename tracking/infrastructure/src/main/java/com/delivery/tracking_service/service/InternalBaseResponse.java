package com.delivery.tracking_service.service;

public record InternalBaseResponse<T>(int status, String message, T data) {
}
