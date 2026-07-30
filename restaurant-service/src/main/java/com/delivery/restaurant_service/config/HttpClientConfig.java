package com.delivery.restaurant_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {
    private final OrderCallResilienceProperties properties;

    public HttpClientConfig(OrderCallResilienceProperties properties) {
        this.properties = properties;
    }

    @Bean
    @LoadBalanced
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(java.time.Duration.ofMillis(properties.getTimeoutMs()))
                .readTimeout(java.time.Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }
}
