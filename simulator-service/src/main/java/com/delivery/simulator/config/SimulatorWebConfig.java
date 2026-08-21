package com.delivery.simulator.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(prefix = "simulator", name = "enabled", havingValue = "true")
public class SimulatorWebConfig implements WebMvcConfigurer {

    private final SimulatorProperties properties;

    public SimulatorWebConfig(SimulatorProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = properties.getAllowedOrigins().stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .toArray(String[]::new);
        if (origins.length == 0) {
            return;
        }
        registry.addMapping("/api/simulator/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept", "X-Simulator-Token", "Last-Event-ID")
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(300);
    }
}
