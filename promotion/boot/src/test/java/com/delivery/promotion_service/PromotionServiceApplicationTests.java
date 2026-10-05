package com.delivery.promotion_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PromotionServiceApplicationTests {
	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.containsBeanDefinition("legacyPromotionMerchantCreateController")).isFalse();
	}

}
