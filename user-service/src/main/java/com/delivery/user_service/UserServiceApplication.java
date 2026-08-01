package com.delivery.user_service;

import java.time.Duration;

import com.delivery.user_service.config.AuthServiceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder, AuthServiceConfig config) {
		return builder
				.connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
				.readTimeout(Duration.ofMillis(config.getTimeoutMs()))
				.build();
	}

}
