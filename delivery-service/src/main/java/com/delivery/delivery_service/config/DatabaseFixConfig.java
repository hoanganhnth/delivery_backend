package com.delivery.delivery_service.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseFixConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixStatusCheckConstraint() {
        try {
            log.info("🔧 Attempting to drop outdated deliveries_status_check constraint...");
            jdbcTemplate.execute("ALTER TABLE deliveries DROP CONSTRAINT IF EXISTS deliveries_status_check");
            log.info("✅ Dropped deliveries_status_check constraint successfully.");
        } catch (Exception e) {
            log.warn("⚠️ Could not drop deliveries_status_check. It might not exist. Error: {}", e.getMessage());
        }
    }
}
