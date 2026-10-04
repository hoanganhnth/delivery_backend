package com.delivery.settlement_service.payment;

import com.delivery.settlement.application.api.*;
import com.delivery.settlement_service.payment.contract.PayOsClient;
import com.delivery.settlement_service.repository.PaymentOrderRepository;
import com.delivery.settlement_service.service.*;
import com.delivery.settlement_service.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PaymentWorkflowCompositionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Host.class);
    @Test void defaultAndDisabledPaymentHaveNoWorkflowOrProviderExecutionBeans() {
        runner.run(this::assertOff);
        runner.withPropertyValues("app.payment.processing-enabled=false").run(this::assertOff);
    }
    @Test void processingFlagAloneComposesExactlyOneTransactionalRealCoreWorkflow() {
        runner.withPropertyValues("app.payment.processing-enabled=true", "app.settlement.application-api-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentWorkflowPort.class).hasSingleBean(PaymentService.class);
                    assertThat(org.springframework.aop.support.AopUtils.isAopProxy(context.getBean(PaymentWorkflowPort.class))).isTrue();
                    assertNoContractExecution(context);
                });
    }
    private void assertOff(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean(PaymentWorkflowPort.class).doesNotHaveBean(PaymentService.class);
        assertNoContractExecution(context);
    }
    private void assertNoContractExecution(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean(PayOsClient.class).doesNotHaveBean(PaymentProviderPort.class)
                .doesNotHaveBean(PayoutProviderPort.class).doesNotHaveBean(PaymentUseCase.class).doesNotHaveBean(PayoutUseCase.class);
    }
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(PaymentServiceImpl.class)
    static class Host {
        @Bean PaymentOrderRepository payments() { return mock(PaymentOrderRepository.class); }
        @Bean PaymentProviderRegistry providers() { return mock(PaymentProviderRegistry.class); }
        @Bean TransactionService ledger() { return mock(TransactionService.class); }
        @Bean PaymentEventPublisher events() { return mock(PaymentEventPublisher.class); }
        @Bean PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() { return new Object(); }
                protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}
                protected void doCommit(DefaultTransactionStatus status) {}
                protected void doRollback(DefaultTransactionStatus status) {}
            };
        }
    }
}
