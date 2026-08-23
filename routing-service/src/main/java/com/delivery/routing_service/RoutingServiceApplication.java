package com.delivery.routing_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.delivery.routing_service.config.RoutingProperties;

@SpringBootApplication
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingServiceApplication.class, args);
    }
}
