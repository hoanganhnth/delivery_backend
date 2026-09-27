package com.delivery.restaurant.infrastructure.decision;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit host wiring for the restaurant order decision and outbox adapter. */
@Configuration
@Import({RestaurantOrderEventPublisher.class, RestaurantDecisionLock.class})
public class RestaurantDecisionInfrastructureConfiguration {
}
