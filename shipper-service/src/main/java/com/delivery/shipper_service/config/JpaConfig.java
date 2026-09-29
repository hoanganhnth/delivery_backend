package com.delivery.shipper_service.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {"com.delivery.shipper.infrastructure.entity"})
@EnableJpaRepositories(basePackages = {"com.delivery.shipper.infrastructure.repository"})
public class JpaConfig {
}
