package com.delivery.restaurant_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/** Serializes the first decision for one order across all restaurant-service instances. */
@Component
@RequiredArgsConstructor
public class RestaurantDecisionLock {

    private final JdbcTemplate jdbcTemplate;

    public void lock(Long orderId) {
        if (isPostgreSql()) {
            jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, orderId);
        }
        // H2 is used only by isolated tests. PostgreSQL is the production database and
        // holds this advisory lock until the surrounding transaction completes.
    }

    private boolean isPostgreSql() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((Connection connection) -> {
            try {
                return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot identify restaurant database", e);
            }
        }));
    }
}
