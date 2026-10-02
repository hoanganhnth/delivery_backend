package com.delivery.shipper.infrastructure.client;

public record TrackingAvailabilityResponse<T>(int status, T data, String message) { }
