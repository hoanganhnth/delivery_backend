package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.service.LocationService;
import com.mapbox.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.api.geocoding.v5.models.GeocodingResponse;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
@Service
public class LocationServiceImpl implements LocationService {

    private final MapboxGeocoding.Builder geocodingBuilder;

    public LocationServiceImpl(MapboxGeocoding.Builder geocodingBuilder) {
        this.geocodingBuilder = geocodingBuilder;
    }

    @Override
    public String getLocationDetails(String address) {
        try {
            MapboxGeocoding geocoding = geocodingBuilder
                    .query(address)
                    .build();

            Response<GeocodingResponse> response = geocoding.executeCall();

            if (response.isSuccessful() && response.body() != null) {
                CarmenFeature feature = response.body().features().get(0); // lấy kết quả đầu tiên
                if (feature.center() != null) {
                    // center() trả về Point (tọa độ theo thứ tự longitude, latitude)
                    double longitude = feature.center().longitude();
                    double latitude = feature.center().latitude();
                    return latitude + ", " + longitude;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "Location not found";
    }

    @Override
    public CompletableFuture<String> getLocationDetailsAsync(String address) {
        return CompletableFuture.supplyAsync(() -> getLocationDetails(address));
    }
}