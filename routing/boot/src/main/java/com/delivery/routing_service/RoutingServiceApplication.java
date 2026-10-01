package com.delivery.routing_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.delivery.routing.infrastructure.config.RoutingInfrastructureConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RoutingInfrastructureConfiguration.class)
public class RoutingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingServiceApplication.class, args);
    }
}
