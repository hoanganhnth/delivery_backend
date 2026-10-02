package com.delivery.restaurant.infrastructure.rating;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Serializes all rating mutations for one restaurant across service instances.
 *
 * <p>The lock is transaction-scoped in PostgreSQL. H2 is used for isolated
 * tests only and intentionally skips the PostgreSQL-specific advisory lock.
 */
@Component
@RequiredArgsConstructor
public class RestaurantRatingLock {

    private static final int LOCK_NAMESPACE = 0x52415445;

    private final JdbcTemplate jdbcTemplate;

    public void lock(Long restaurantId) {
        if (restaurantId == null || restaurantId <= 0) {
            throw new IllegalArgumentException("restaurantId must be positive");
        }
        if (isPostgreSql()) {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_xact_lock(?, ?)",
                    Object.class,
                    LOCK_NAMESPACE,
                    // PostgreSQL's two-key overload accepts two int32 values.
                    // Fold the full identity deterministically; collisions only
                    // serialize unrelated restaurants, and low IDs keep their key.
                    Long.hashCode(restaurantId));
        }
    }

    private boolean isPostgreSql() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((Connection connection) -> {
            try {
                return connection.getMetaData().getDatabaseProductName()
                        .toLowerCase()
                        .contains("postgresql");
            } catch (SQLException exception) {
                throw new IllegalStateException("Cannot identify restaurant database", exception);
            }
        }));
    }
}
