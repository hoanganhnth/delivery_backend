package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.PublisherTaskSchedulePort;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class TaskSchedulerPublisherAdapter implements PublisherTaskSchedulePort {
    private final TaskScheduler scheduler;
    public TaskSchedulerPublisherAdapter(@Qualifier("publisherGraceTaskScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }
    @Override public void schedule(Runnable task, Instant deadline) {
        scheduler.schedule(task, deadline);
    }
}
