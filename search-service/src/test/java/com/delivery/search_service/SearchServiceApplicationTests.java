package com.delivery.search_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.elasticsearch.enabled=false",
		"spring.data.elasticsearch.repositories.enabled=false",
		"spring.kafka.listener.auto-startup=false"
})
class SearchServiceApplicationTests {
	@Test
	void contextLoads() {
	}

}
