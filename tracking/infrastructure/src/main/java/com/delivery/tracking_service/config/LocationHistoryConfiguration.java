package com.delivery.tracking_service.config;
import com.delivery.tracking.application.DefaultLocationHistoryUseCase;
import com.delivery.tracking.application.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
public class LocationHistoryConfiguration {
    @Bean LocationHistoryUseCase locationHistory(LocationHistoryStorePort store, LocationHistoryTransactionPort transactions,
            @Value("${app.location-history.max-query-size:500}") int maxSize,
            @Value("${app.location-history.retention-days:90}") int retentionDays) {
        return new DefaultLocationHistoryUseCase(store, transactions, maxSize, retentionDays);
    }
}
