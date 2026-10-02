package com.delivery.restaurant.infrastructure.client;

import com.delivery.restaurant.application.api.OrderDecisionEligibilityPort;

import com.delivery.restaurant.application.api.RatingOrderEligibilityPort;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/** Spring wiring for Order's internal HTTP adapters. */
@Configuration
@EnableConfigurationProperties(OrderCallResilienceProperties.class)
public class OrderInfrastructureConfiguration {

    @Bean
    @LoadBalanced
    RestTemplate orderRestTemplate(RestTemplateBuilder builder, OrderCallResilienceProperties properties) {
        return builder
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    @Bean
    RestaurantOrderCircuitBreaker restaurantOrderCircuitBreaker(
            OrderCallResilienceProperties properties, MeterRegistry meterRegistry) {
        return new RestaurantOrderCircuitBreaker(properties, meterRegistry);
    }

    @Bean
    RatingOrderEligibilityPort orderEligibilityPort(
            RestTemplate orderRestTemplate,
            @Value("${order.service.url}") String orderServiceUrl,
            @Value("${app.internal.secret:}") String internalSecret,
            RestaurantOrderCircuitBreaker circuitBreaker) {
        return new OrderEligibilityClient(orderRestTemplate, orderServiceUrl, internalSecret, circuitBreaker);
    }

    @Bean
    OrderDecisionEligibilityPort orderDecisionEligibilityPort(
            RestTemplate orderRestTemplate,
            @Value("${order.service.url}") String orderServiceUrl,
            @Value("${app.internal.secret:}") String internalSecret,
            RestaurantOrderCircuitBreaker circuitBreaker) {
        return new OrderDecisionEligibilityClient(orderRestTemplate, orderServiceUrl, internalSecret, circuitBreaker);
    }
}
