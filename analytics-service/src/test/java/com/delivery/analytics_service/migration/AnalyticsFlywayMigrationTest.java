package com.delivery.analytics_service.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsFlywayMigrationTest {

    @Test
    void cleanSchemaCreatesDedupScopeConstraintsAndQueryIndexes() throws Exception {
        String url = databaseUrl("clean");
        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertEvent(statement, "event-1");
            insertOrderStats(statement, "2026-07-24", null);
            insertRevenueStats(statement, "2026-07-24", null);

            assertThatThrownBy(() -> insertEvent(statement, "event-1"))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThatThrownBy(() -> insertOrderStats(statement, "2026-07-24", null))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThatThrownBy(() -> insertRevenueStats(statement, "2026-07-24", null))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThat(indexExists(connection, "analytics_events", "idx_event_type_time")).isTrue();
            assertThat(indexExists(connection, "analytics_events", "idx_restaurant_id")).isTrue();
            assertThat(indexExists(connection, "analytics_events", "idx_event_time")).isTrue();
            assertThat(indexExists(connection, "daily_order_stats", "idx_daily_order_date")).isTrue();
            assertThat(indexExists(connection, "daily_revenue_stats", "idx_daily_revenue_date")).isTrue();
        }
    }

    @Test
    void legacySchemaPreservesRawEventsAndDailyRows() throws Exception {
        String url = databaseUrl("legacy");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertEvent(statement, "legacy-event");
            insertOrderStats(statement, "2026-07-23", null);
            insertRevenueStats(statement, "2026-07-23", 42L);
        }

        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT count(*) FROM analytics_events")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM daily_order_stats")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM daily_revenue_stats")).isEqualTo(1);
        }
    }

    @Test
    void duplicateLegacyPlatformOrderScopeStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_platform_order");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertOrderStats(statement, "2026-07-23", null);
            insertOrderStats(statement, "2026-07-23", null);
        }

        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate daily order scope");
    }

    @Test
    void duplicateLegacyPlatformRevenueScopeStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_platform_revenue");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertRevenueStats(statement, "2026-07-23", null);
            insertRevenueStats(statement, "2026-07-23", null);
        }

        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate daily revenue scope");
    }

    @Test
    void missingCoreColumnStopsMigration() throws Exception {
        String url = databaseUrl("missing_core");
        createLegacySchema(url, true);

        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("missing core column").hasMessageContaining("raw_payload");
    }

    @Test
    void incompleteTableSetStopsMigration() throws Exception {
        String url = databaseUrl("incomplete");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            createAnalyticsEvents(statement, false);
        }

        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("schema is incomplete");
    }

    private void migrate(String url) {
        Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("0"))
                .load().migrate();
    }

    private void createLegacySchema(String url, boolean omitRawPayload) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            createAnalyticsEvents(statement, omitRawPayload);
            statement.execute("""
                    CREATE TABLE daily_order_stats (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        stat_date DATE NOT NULL, restaurant_id BIGINT,
                        total_orders BIGINT NOT NULL, delivered_orders BIGINT NOT NULL,
                        cancelled_orders BIGINT NOT NULL, pending_orders BIGINT NOT NULL,
                        total_revenue DECIMAL(15,2), total_shipping_fee DECIMAL(15,2),
                        total_discount DECIMAL(15,2), avg_order_value DECIMAL(12,2),
                        new_customers BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE daily_revenue_stats (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        stat_date DATE NOT NULL, restaurant_id BIGINT,
                        total_payment_amount DECIMAL(15,2), successful_payments BIGINT NOT NULL,
                        failed_payments BIGINT NOT NULL, total_withdrawals DECIMAL(15,2),
                        platform_fee DECIMAL(15,2)
                    )
                    """);
        }
    }

    private void createAnalyticsEvents(Statement statement, boolean omitRawPayload) throws Exception {
        statement.execute("""
                CREATE TABLE analytics_events (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    deduplication_key VARCHAR(160) NOT NULL, event_type VARCHAR(50) NOT NULL,
                    event_time TIMESTAMP NOT NULL, order_id BIGINT, user_id BIGINT,
                    restaurant_id BIGINT, shipper_id BIGINT, amount DECIMAL(15,2),
                    order_status VARCHAR(30), payment_method VARCHAR(30),
                    restaurant_name VARCHAR(255) %s
                )
                """.formatted(omitRawPayload ? "" : ", raw_payload TEXT"));
    }

    private void insertEvent(Statement statement, String key) throws Exception {
        statement.executeUpdate("""
                INSERT INTO analytics_events (deduplication_key, event_type, event_time)
                VALUES ('%s', 'ORDER_CREATED', CURRENT_TIMESTAMP)
                """.formatted(key));
    }

    private void insertOrderStats(Statement statement, String date, Long restaurantId) throws Exception {
        statement.executeUpdate("""
                INSERT INTO daily_order_stats
                    (stat_date, restaurant_id, total_orders, delivered_orders, cancelled_orders,
                     pending_orders, new_customers)
                VALUES ('%s', %s, 1, 0, 0, 1, 0)
                """.formatted(date, sqlLong(restaurantId)));
    }

    private void insertRevenueStats(Statement statement, String date, Long restaurantId) throws Exception {
        statement.executeUpdate("""
                INSERT INTO daily_revenue_stats
                    (stat_date, restaurant_id, successful_payments, failed_payments)
                VALUES ('%s', %s, 1, 0)
                """.formatted(date, sqlLong(restaurantId)));
    }

    private String sqlLong(Long value) {
        return value == null ? "NULL" : value.toString();
    }

    private boolean indexExists(Connection connection, String table, String expectedName) throws Exception {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                null, connection.getSchema(), table, false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null && expectedName.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String databaseUrl(String label) {
        return "jdbc:h2:mem:analytics_migration_" + label + "_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    }
}
