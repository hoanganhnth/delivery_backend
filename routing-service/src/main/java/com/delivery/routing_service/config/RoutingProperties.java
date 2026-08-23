package com.delivery.routing_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "routing")
public class RoutingProperties {

    private String mapboxToken;
    private String mapboxBaseUrl = "https://api.mapbox.com";
    private int providerTimeoutMs = 450;
    private int cacheTtlSeconds = 60;
    private int fallbackSpeedKmh = 18;
    private String internalSecret;
}
