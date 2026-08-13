package com.delivery.shipper_service.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** HTTP client used only for Shipper's internal availability command. */
@Configuration
public class HttpClientConfig {

    @Bean("trackingAvailabilityRestTemplate")
    RestTemplate trackingAvailabilityRestTemplate(
            RestTemplateBuilder builder,
            @Value("${app.shipper.tracking-service.timeout-ms:1500}") long timeoutMs) {
        Duration timeout = Duration.ofMillis(Math.max(1, timeoutMs));
        return builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }
}
