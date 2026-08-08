package com.delivery.tracking_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.kafka.listener.auto-startup=false",
		"app.websocket.redis-fanout-listener-enabled=false",
		"spring.datasource.url=jdbc:h2:mem:tracking_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"delivery.service.url=http://delivery-service",
		"app.internal.secret=test-secret",
		"app.auth.jwks-uri=http://localhost:8081/.well-known/jwks.json"
})
class TrackingServiceApplicationTests {
	@Autowired ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

}
