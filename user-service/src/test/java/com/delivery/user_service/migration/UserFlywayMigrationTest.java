package com.delivery.user_service.migration;

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

class UserFlywayMigrationTest {

    @Test
    void cleanSchemaCreatesProvisioningConstraintAndAddressIndexes() throws Exception {
        String url = databaseUrl("clean");
        migrate(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long userId = insertUser(statement, 10, "user-10@example.com");
            insertAddress(statement, userId, true);
            assertThatThrownBy(() -> insertUser(statement, 10, "duplicate@example.com"))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThatThrownBy(() -> insertUser(statement, 11, "USER-10@example.com"))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThat(indexExists(connection, "idx_user_addresses_user_created")).isTrue();
            assertThat(indexExists(connection, "idx_user_addresses_user_default")).isTrue();
        }
    }

    @Test
    void legacySchemaPreservesUserAndAddress() throws Exception {
        String url = databaseUrl("legacy");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long userId = insertUser(statement, 10, "legacy@example.com");
            insertAddress(statement, userId, true);
        }
        migrate(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT count(*) FROM users")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM user_addresses")).isEqualTo(1);
        }
    }

    @Test
    void duplicateLegacyAuthIdStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_auth");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertUser(statement, 10, "first@example.com");
            insertUser(statement, 10, "second@example.com");
        }
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate auth_id");
    }

    @Test
    void duplicateLegacyEmailStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_email");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertUser(statement, 10, "user@example.com");
            insertUser(statement, 11, "USER@example.com");
        }
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate email");
    }

    @Test
    void duplicateLegacyDefaultAddressesStopMigration() throws Exception {
        String url = databaseUrl("duplicate_default");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long userId = insertUser(statement, 10, "default@example.com");
            insertAddress(statement, userId, true);
            insertAddress(statement, userId, true);
        }
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("multiple default addresses");
    }

    @Test
    void missingCoreColumnStopsMigration() throws Exception {
        String url = databaseUrl("missing_core");
        createLegacySchema(url, true);
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("missing core column").hasMessageContaining("is_default");
    }

    private void migrate(String url) {
        Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("0"))
                .load().migrate();
    }

    private void createLegacySchema(String url, boolean omitDefault) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE users (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        auth_id BIGINT NOT NULL, email VARCHAR(255) NOT NULL, role VARCHAR(255) NOT NULL,
                        full_name VARCHAR(255), phone VARCHAR(255), dob DATE, avatar_url VARCHAR(255),
                        address VARCHAR(255), is_active BOOLEAN, is_blocked BOOLEAN, blocked_at TIMESTAMP,
                        blocked_by BIGINT, block_reason TEXT, created_at TIMESTAMP, updated_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE user_addresses (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        user_id BIGINT, label VARCHAR(255), recipient_name VARCHAR(255),
                        phone_number VARCHAR(255), address_line VARCHAR(255), ward VARCHAR(255),
                        district VARCHAR(255), city VARCHAR(255), postal_code VARCHAR(255),
                        latitude DOUBLE PRECISION, longitude DOUBLE PRECISION,
                        %s
                        created_at TIMESTAMP, updated_at TIMESTAMP
                    )
                    """.formatted(omitDefault ? "" : "is_default BOOLEAN,"));
        }
    }

    private long insertUser(Statement statement, long authId, String email) throws Exception {
        statement.executeUpdate("""
                INSERT INTO users (auth_id, email, role, is_active, is_blocked, created_at, updated_at)
                VALUES (%d, '%s', 'USER', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(authId, email), Statement.RETURN_GENERATED_KEYS);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertThat(keys.next()).isTrue();
            return keys.getLong(1);
        }
    }

    private void insertAddress(Statement statement, long userId, boolean isDefault) throws Exception {
        statement.executeUpdate("""
                INSERT INTO user_addresses (user_id, label, address_line, is_default, created_at, updated_at)
                VALUES (%d, 'Home', 'Address', %s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(userId, isDefault));
    }

    private boolean indexExists(Connection connection, String expectedName) throws Exception {
        try (ResultSet indexes = connection.getMetaData()
                .getIndexInfo(null, null, "user_addresses", false, false)) {
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
        return "jdbc:h2:mem:user_migration_" + label + "_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    }
}
