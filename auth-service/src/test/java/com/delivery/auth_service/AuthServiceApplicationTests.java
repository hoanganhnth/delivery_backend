package com.delivery.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

	@DynamicPropertySource
	static void jwtKeyProperties(DynamicPropertyRegistry registry) {
		TestJwtKeyProperties.register(registry);
	}

	@Test
	void contextLoads() {
	}

}
