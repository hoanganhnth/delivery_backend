package com.delivery.auth_service;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import com.delivery.auth_service.config.UserServiceConfig;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

	@Bean
	@LoadBalanced
	public RestTemplate restTemplate(RestTemplateBuilder builder, UserServiceConfig userServiceConfig) {
		return builder
				.connectTimeout(Duration.ofMillis(userServiceConfig.getTimeoutMs()))
				.readTimeout(Duration.ofMillis(userServiceConfig.getTimeoutMs()))
				.build();
	}
}
