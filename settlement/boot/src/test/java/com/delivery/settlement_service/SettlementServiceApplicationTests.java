package com.delivery.settlement_service;

import com.delivery.settlement_service.controller.PaymentController;
import com.delivery.settlement_service.controller.BalanceController;
import com.delivery.settlement_service.controller.TransactionController;
import com.delivery.settlement_service.controller.FakePaymentController;
import com.delivery.settlement_service.payment.PaymentProviderRegistry;
import com.delivery.settlement_service.payment.provider.FakePaymentProvider;
import com.delivery.settlement_service.payment.provider.VnPayProvider;
import com.delivery.settlement_service.service.PaymentEventPublisher;
import com.delivery.settlement_service.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SettlementServiceApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(PaymentController.class)).isEmpty();
		assertThat(context.getBeansOfType(FakePaymentController.class)).isEmpty();
		assertThat(context.getBeansOfType(PaymentProviderRegistry.class)).isEmpty();
		assertThat(context.getBeansOfType(FakePaymentProvider.class)).isEmpty();
		assertThat(context.getBeansOfType(VnPayProvider.class)).isEmpty();
		assertThat(context.getBeansOfType(PaymentEventPublisher.class)).isEmpty();
		assertThat(context.getBeansOfType(PaymentServiceImpl.class)).isEmpty();
		assertThat(context.getBeansOfType(BalanceController.class)).isEmpty();
		assertThat(context.getBeansOfType(TransactionController.class)).isEmpty();
		assertThat(context.containsBeanDefinition("legacySettlementAdminMutationController")).isFalse();
	}

}
