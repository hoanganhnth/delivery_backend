package com.delivery.tracking_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PublisherSessionConfig {

    @Bean("publisherGraceTaskScheduler")
    public ThreadPoolTaskScheduler publisherGraceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("tracking-publisher-grace-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
