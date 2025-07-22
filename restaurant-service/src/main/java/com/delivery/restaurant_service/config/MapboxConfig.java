package com.delivery.restaurant_service.config;

import com.mapbox.api.geocoding.v5.MapboxGeocoding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapboxConfig {

    @Value("${mapbox.access-token}")
    private String mapboxAccessToken;

    @Bean
    public MapboxGeocoding.Builder mapboxGeocoding() {
        // You can configure other Mapbox services here as needed
        return MapboxGeocoding.builder()
                .accessToken(mapboxAccessToken);
    }

    // You can add other Mapbox service builders (e.g., MapboxDirections, MapboxMatrix)
    // as @Beans if you need them in your application.
}