package com.delivery.settlement_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.refund.outbox-relay-enabled", havingValue = "true")
public class RefundSchedulingConfig {
}
