package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/geocode")
    public ResponseEntity<String> geocodeAddress(@RequestParam String address) {
        String result = locationService.getLocationDetails(address);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/geocode-async-true")
    public CompletableFuture<ResponseEntity<String>> geocodeAsyncReal(@RequestParam String address) {
        return locationService.getLocationDetailsAsync(address)
                .thenApply(ResponseEntity::ok);
    }
}