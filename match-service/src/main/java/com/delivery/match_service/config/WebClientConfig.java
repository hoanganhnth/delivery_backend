package com.delivery.match_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * ✅ WebClient Configuration cho HTTP calls đến các services khác
 * Theo Backend Instructions: Proper configuration classes
 */
@Configuration
public class WebClientConfig {
    
    @Value("${settlement.service.url}")
    private String settlementServiceUrl;

    @Value("${app.internal.secret:}")
    private String internalSecret;
    
    @Bean
    @Qualifier("settlementServiceWebClient")
    public WebClient settlementServiceWebClient() {
        return WebClient.builder()
                .baseUrl(settlementServiceUrl)
                .defaultHeader("Internal-Token", internalSecret)
                .build();
    }
}
