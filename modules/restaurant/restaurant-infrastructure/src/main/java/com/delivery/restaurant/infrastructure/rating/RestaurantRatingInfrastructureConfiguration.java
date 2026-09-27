package com.delivery.restaurant.infrastructure.rating;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit host wiring for rating persistence and concurrency adapters. */
@Configuration
@Import({RestaurantRatingServiceImpl.class, RestaurantRatingLock.class})
public class RestaurantRatingInfrastructureConfiguration {
}
