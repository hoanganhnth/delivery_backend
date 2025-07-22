package com.delivery.restaurant_service.service;

import java.util.concurrent.CompletableFuture;

public interface LocationService {
    String getLocationDetails(String address);
    CompletableFuture<String> getLocationDetailsAsync(String address);
}
