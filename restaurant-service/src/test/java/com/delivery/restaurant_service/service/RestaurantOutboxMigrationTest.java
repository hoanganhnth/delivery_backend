package com.delivery.restaurant_service.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantOutboxMigrationTest {

    @Test
    void migrationAloneCreatesDecisionAndOutboxSchema() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:restaurant_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO restaurant_order_decisions(order_id, restaurant_id, decision, created_at)
                VALUES (?, ?, ?, ?)
                """, 41L, 7L, "CONFIRMED", now);
        jdbc.update("""
                INSERT INTO restaurant_outbox_events(
                    event_id, aggregate_id, event_type, topic, event_key, payload,
                    status, attempts, next_attempt_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), "41", "CONFIRMED", "restaurant.order-confirmed",
                "41", "{}", "PENDING", 0, now, now);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurant_order_decisions", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'restaurant_order_decisions'
                  AND LOWER(COLUMN_NAME) = 'payload_fingerprint'
                """, Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurant_outbox_events", Long.class))
                .isEqualTo(1L);
    }
}
