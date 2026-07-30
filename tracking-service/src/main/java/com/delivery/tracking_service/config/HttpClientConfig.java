package com.delivery.tracking_service.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {
    private final DeliveryCallResilienceProperties properties;

    public HttpClientConfig(DeliveryCallResilienceProperties properties) {
        this.properties = properties;
    }

    @Bean
    @LoadBalanced
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }
}
