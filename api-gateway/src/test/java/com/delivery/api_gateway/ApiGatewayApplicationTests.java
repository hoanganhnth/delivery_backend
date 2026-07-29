package com.delivery.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ApiGatewayApplicationTests {

	@DynamicPropertySource
	static void jwtKeyProperties(DynamicPropertyRegistry registry) {
		TestJwtPublicKeyProperties.register(registry);
	}

	@Test
	void contextLoads() {
	}

}
