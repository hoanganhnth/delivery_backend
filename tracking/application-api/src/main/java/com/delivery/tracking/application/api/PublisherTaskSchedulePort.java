package com.delivery.tracking.application.api;

import java.time.Instant;

public interface PublisherTaskSchedulePort {
    void schedule(Runnable task, Instant deadline);
}
