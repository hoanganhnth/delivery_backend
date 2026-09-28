package com.delivery.match_service.config;

import com.delivery.match.application.DefaultDispatchMatchingService;
import com.delivery.match.application.api.DispatchMatchingPort;
import com.delivery.match.application.api.FindNearbyShippersPort;
import com.delivery.match_service.service.MatchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runtime adapter wiring for framework-free match use cases. */
@Configuration
public class MatchApplicationConfig {
    @Bean
    DispatchMatchingPort dispatchMatchingPort() {
        return new DefaultDispatchMatchingService();
    }

    @Bean
    FindNearbyShippersPort findNearbyShippersPort(MatchService matchService) {
        return matchService::findNearbyShippers;
    }
}
