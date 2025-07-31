package com.delivery.match_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ✅ WebClient Configuration cho HTTP calls đến các services khác
 * Theo Backend Instructions: Proper configuration classes
 */
@Configuration
public class WebClientConfig {
    
    @Value("${tracking.service.url}")
    private String trackingServiceUrl;
    
    /**
     * ✅ WebClient bean để call Tracking Service
     * Constructor injection pattern sẽ được dùng ở service layer
     */
    @Bean
    public WebClient trackingServiceWebClient() {
        return WebClient.builder()
                .baseUrl(trackingServiceUrl)
                .build();
    }
}
