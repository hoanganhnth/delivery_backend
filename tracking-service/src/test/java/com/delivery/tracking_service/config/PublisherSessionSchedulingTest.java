package com.delivery.tracking_service.config;

import com.delivery.tracking.application.api.PublisherSessionUseCase;
import com.delivery.tracking_service.service.LocationHistoryRetentionJob;
import com.delivery.tracking.application.api.LocationHistoryUseCase;
import com.delivery.tracking_service.service.PublisherLeaseExpirySweeper;
import com.delivery.tracking_service.service.TaskSchedulerPublisherAdapter;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PublisherSessionSchedulingTest {
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(PublisherSessionConfig.class, Jobs.class);

    @Test
    void disabledPeriodicJobsStillAllowExplicitDisconnectGraceTasks() {
        contexts.withPropertyValues("spring.task.scheduling.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
            var completed = new CountDownLatch(1);
            var scheduler = context.getBean("publisherGraceTaskScheduler", ThreadPoolTaskScheduler.class);
            new TaskSchedulerPublisherAdapter(scheduler).schedule(completed::countDown, Instant.now());
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            verifyNoInteractions(context.getBean(PublisherSessionUseCase.class),
                    context.getBean(LocationHistoryUseCase.class));
        });
    }

    @Test
    void runtimeDefaultRegistersBothPeriodicJobs() {
        contexts.run(context -> assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class)
                .getScheduledTasks()).hasSize(2));
    }

    @Test
    void explicitEnableRegistersBothPeriodicJobs() {
        contexts.withPropertyValues("spring.task.scheduling.enabled=true").run(context ->
                assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class)
                        .getScheduledTasks()).hasSize(2));
    }

    @Configuration(proxyBeanMethods = false)
    static class Jobs {
        @Bean PublisherSessionUseCase publishers() { return mock(PublisherSessionUseCase.class); }
        @Bean LocationHistoryUseCase history() { return mock(LocationHistoryUseCase.class); }
        @Bean PublisherLeaseExpirySweeper sweeper(PublisherSessionUseCase publishers) {
            return new PublisherLeaseExpirySweeper(publishers);
        }
        @Bean LocationHistoryRetentionJob retention(LocationHistoryUseCase history) {
            return new LocationHistoryRetentionJob(history);
        }
    }
}
