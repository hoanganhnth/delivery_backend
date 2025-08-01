package com.delivery.match_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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
     * ✅ WebClient bean để call Tracking Service với flexible content type handling
     * Constructor injection pattern sẽ được dùng ở service layer
     */
    @Bean
    public WebClient trackingServiceWebClient() {
        return WebClient.builder()
                .baseUrl(trackingServiceUrl)
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> {
                            // ✅ Accept both application/json và text/plain responses
                            configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder());
                            configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder());
                            configurer.defaultCodecs().maxInMemorySize(1024 * 1024); // 1MB buffer
                        })
                        .build())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE + ", " + MediaType.TEXT_PLAIN_VALUE)
                .build();
    }
}
