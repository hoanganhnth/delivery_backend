package com.delivery.flashsale_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import com.delivery.flashsale_service.controller.MerchantFlashSaleController;
import com.delivery.flashsale_service.config.RedisConfig;
import com.delivery.flashsale_service.service.FlashSaleStockService;

@SpringBootTest
@ActiveProfiles("test")
class FlashsaleServiceApplicationTests {

	@Autowired
	ApplicationContext context;

	@Test
	void contextLoads() {
		org.assertj.core.api.Assertions.assertThat(
				context.getBeansOfType(MerchantFlashSaleController.class)).isEmpty();
		org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(RedisConfig.class)).isEmpty();
		org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(FlashSaleStockService.class)).isEmpty();
	}

}
