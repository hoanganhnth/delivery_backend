package com.delivery.settlement_service.payment;

import com.delivery.settlement_service.controller.FakePaymentController;
import com.delivery.settlement_service.payment.provider.FakePaymentProvider;
import com.delivery.settlement_service.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FakePaymentProfileIsolationTest {

    @Test
    void productionProfileCannotEnableFakePaymentBeans() {
        try (var context = context("prod")) {
            assertThat(context.getBeansOfType(FakePaymentController.class)).isEmpty();
            assertThat(context.getBeansOfType(FakePaymentProvider.class)).isEmpty();
        }
    }

    @Test
    void testProfileStillRequiresExplicitFlagsAndCanEnableFixture() {
        try (var context = context("test")) {
            assertThat(context.getBeansOfType(FakePaymentController.class)).hasSize(1);
            assertThat(context.getBeansOfType(FakePaymentProvider.class)).hasSize(1);
        }
    }

    private AnnotationConfigApplicationContext context(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        TestPropertyValues.of(
                "app.payment.processing-enabled=true",
                "app.payment.fake-provider-enabled=true",
                "server.port=8095")
                .applyTo(context);
        context.registerBean(PaymentService.class, () -> mock(PaymentService.class));
        context.register(FakePaymentController.class, FakePaymentProvider.class);
        context.refresh();
        return context;
    }
}
