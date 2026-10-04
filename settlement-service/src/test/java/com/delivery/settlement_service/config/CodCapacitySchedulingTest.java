package com.delivery.settlement_service.config;

import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.CodCapacityHoldRepository;
import com.delivery.settlement_service.service.CodCapacityHoldService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CodCapacitySchedulingTest {
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeScheduling.class, Jobs.class);

    @Test void defaultRuntimeSchedulesCodExpiryWithRefundRelayDisabled() {
        contexts.withPropertyValues("app.refund.outbox-relay-enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).hasSize(1);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
        });
    }
    @Test void explicitRuntimeEnableSchedulesCodExpiry() {
        contexts.withPropertyValues("spring.task.scheduling.enabled=true", "app.refund.outbox-relay-enabled=false")
                .run(context -> assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class)
                        .getScheduledTasks()).hasSize(1));
    }
    @Test void disabledSchedulingIsRespectedEvenWhenRefundRelayIsEnabled() {
        contexts.withPropertyValues("spring.task.scheduling.enabled=false", "app.refund.outbox-relay-enabled=true")
                .run(context -> assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty());
    }
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = CodSettlementConfiguration.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "com\\.delivery\\.settlement_service\\.config\\.[A-Za-z]*SchedulingConfig"))
    static class RuntimeScheduling {}
    @Configuration(proxyBeanMethods = false)
    static class Jobs {
        @Bean CodCapacityHoldService codHolds() {
            return new CodCapacityHoldService(mock(BalanceRepository.class), mock(CodCapacityHoldRepository.class));
        }
    }
}
