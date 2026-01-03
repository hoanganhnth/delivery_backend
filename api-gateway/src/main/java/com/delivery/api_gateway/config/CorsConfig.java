package com.delivery.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
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

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // ✅ Allow frontend origins
        corsConfig.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",     // Vite default
            "http://localhost:3000",     // React default
            "http://localhost:4200",     // Angular default
            "http://localhost:8080",     // Vue default
            "http://127.0.0.1:5173",
            "http://127.0.0.1:3000"
        ));
        
        // ✅ Allow all HTTP methods
        corsConfig.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // ✅ Allow all headers (including Authorization, X-User-Id, X-Role)
        corsConfig.setAllowedHeaders(List.of("*"));
        
        // ✅ Allow credentials (cookies, authorization headers)
        corsConfig.setAllowCredentials(true);
        
        // ✅ Cache preflight response for 1 hour
        corsConfig.setMaxAge(3600L);
        
        // ✅ Expose headers to frontend
        corsConfig.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-User-Id", 
            "X-Role",
            "Content-Type"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        return new CorsWebFilter(source);
    }
}
