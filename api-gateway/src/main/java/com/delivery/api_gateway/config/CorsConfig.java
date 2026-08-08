package com.delivery.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 🌐 CORS Configuration for API Gateway
 * 
 * Allows frontend (React, Vue, Angular) from different origins to call APIs
 * through the API Gateway.
 * 
 * @author DeliveryVN Platform
 */
@Configuration
public class CorsConfig {

    static final String DEFAULT_ALLOWED_ORIGINS = "http://localhost:5173,http://localhost:3000,"
            + "http://localhost:4173,"
            + "http://127.0.0.1:5173,http://127.0.0.1:3000,"
            + "http://127.0.0.1:4173";

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalStateException("At least one CORS origin must be configured");
        }
    }

    List<String> allowedOrigins() {
        return allowedOrigins;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // ✅ Allow frontend origins
        corsConfig.setAllowedOrigins(allowedOrigins);
        
        // ✅ Allow all HTTP methods
        corsConfig.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // ✅ Allow all headers (including Authorization)
        corsConfig.setAllowedHeaders(List.of("*"));
        
        // ✅ Allow credentials (cookies, authorization headers)
        corsConfig.setAllowCredentials(true);
        
        // ✅ Cache preflight response for 1 hour
        corsConfig.setMaxAge(3600L);
        
        // ✅ Expose headers to frontend
        corsConfig.setExposedHeaders(List.of(
            "Authorization",
            "Content-Type"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        return new CorsWebFilter(source);
    }
}
