package com.delivery.routing.infrastructure.config;

import com.delivery.routing.application.DefaultRoutingService;
import com.delivery.routing.application.api.RoutingPort;
import com.delivery.routing.application.api.RoutingProviderPort;
import com.delivery.routing.infrastructure.provider.MapboxRoutingProviderAdapter;
import com.delivery.routing.infrastructure.http.RoutingController;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(RoutingProperties.class)
@Import(RoutingController.class)
public class RoutingInfrastructureConfiguration {
    @Bean
    RoutingProviderPort routingProvider(RoutingProperties properties) {
        return new MapboxRoutingProviderAdapter(properties);
    }

    @Bean
    RoutingPort routing(RoutingProviderPort provider, RoutingProperties properties) {
        return new DefaultRoutingService(provider, properties.getFallbackSpeedKmh());
    }
}
