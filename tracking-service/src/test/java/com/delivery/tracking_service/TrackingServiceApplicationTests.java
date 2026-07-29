package com.delivery.tracking_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.kafka.listener.auto-startup=false",
		"delivery.service.url=http://delivery-service",
		"app.internal.secret=test-secret"
})
class TrackingServiceApplicationTests {
	@Autowired ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

}
