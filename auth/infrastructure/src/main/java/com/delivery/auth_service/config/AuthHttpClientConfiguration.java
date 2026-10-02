package com.delivery.auth_service.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AuthHttpClientConfiguration {
    @Bean @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder, UserServiceConfig config) {
        return builder.connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .readTimeout(Duration.ofMillis(config.getTimeoutMs())).build();
    }
}
