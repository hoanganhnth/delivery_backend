package com.delivery.auth_service.migration;

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

class AuthFlywayMigrationTest {

    @Test
    void cleanSchemaCreatesAccountSessionUniquenessAndIndexes() throws Exception {
        String url = databaseUrl("clean");
        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long accountId = insertAccount(statement, "user@example.com");
            insertSession(statement, accountId, "refresh-1");
            assertThatThrownBy(() -> insertAccount(statement, "user@example.com"))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThatThrownBy(() -> insertSession(statement, accountId, "refresh-1"))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThat(indexExists(connection, "auth_session", "idx_auth_session_account_active_expiry")).isTrue();
            assertThat(indexExists(connection, "auth_session", "idx_auth_session_account_device_active")).isTrue();
            assertThat(columnExists(connection, "auth_session", "token_family_id")).isTrue();
            assertThat(tableExists(connection, "auth_refresh_token")).isTrue();
            assertThat(indexExists(connection, "auth_refresh_token", "idx_auth_refresh_token_session_state")).isTrue();
            assertThat(columnExists(connection, "auth_account", "email_verification_required")).isTrue();
            assertThat(columnExists(connection, "auth_account", "email_verified_at")).isTrue();
            assertThat(tableExists(connection, "auth_security_token")).isTrue();
            assertThat(tableExists(connection, "auth_security_audit")).isTrue();
            assertThat(indexExists(connection, "auth_security_token",
                    "idx_auth_security_token_account_purpose")).isTrue();
            assertThat(indexExists(connection, "auth_security_audit",
                    "idx_auth_security_audit_account_time")).isTrue();
            assertThat(columnExists(connection, "auth_account", "user_status_sync_pending")).isTrue();
            assertThat(columnExists(connection, "auth_account", "user_status_sync_version")).isTrue();
            assertThat(columnExists(connection, "auth_account", "user_status_sync_admin_id")).isTrue();
            assertThat(columnExists(connection, "auth_account", "user_status_sync_block_reason")).isTrue();
            assertThat(columnExists(connection, "auth_account", "user_status_sync_attempts")).isTrue();
            assertThat(columnExists(connection, "auth_account", "user_status_sync_last_error")).isTrue();
            assertThat(columnExists(connection, "auth_account", "user_status_sync_updated_at")).isTrue();
            assertThat(indexExists(connection, "auth_account", "idx_auth_account_user_status_sync_pending")).isTrue();
        }
    }

    @Test
    void legacySchemaPreservesAccountAndSession() throws Exception {
        String url = databaseUrl("legacy");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long accountId = insertAccount(statement, "legacy@example.com");
            insertSession(statement, accountId, "legacy-refresh");
        }

        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT count(*) FROM auth_account")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM auth_session")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM auth_refresh_token")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM auth_session WHERE refresh_token IS NULL"))
                    .isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM auth_session WHERE token_family_id IS NOT NULL"))
                    .isEqualTo(1);
            assertThat(count(statement, """
                    SELECT count(*) FROM auth_account
                    WHERE email_verification_required = false
                      AND email_verified_at IS NOT NULL
                    """)).isEqualTo(1);
            assertThat(count(statement, """
                    SELECT count(*) FROM auth_account
                    WHERE user_status_sync_pending = false
                      AND user_status_sync_version = 0
                      AND user_status_sync_attempts = 0
                    """)).isEqualTo(1);
        }
    }

    @Test
    void duplicateLegacyEmailStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_email");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertAccount(statement, "duplicate@example.com");
            insertAccount(statement, "duplicate@example.com");
        }

        assertThatThrownBy(() -> migrate(url))
                .isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate email");
    }

    @Test
    void duplicateLegacyRefreshTokenStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_refresh");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long accountId = insertAccount(statement, "sessions@example.com");
            insertSession(statement, accountId, "duplicate-refresh");
            insertSession(statement, accountId, "duplicate-refresh");
        }

        assertThatThrownBy(() -> migrate(url))
                .isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate refresh token");
    }

    @Test
    void missingCoreColumnStopsMigration() throws Exception {
        String url = databaseUrl("missing_core");
        createLegacySchema(url, true);

        assertThatThrownBy(() -> migrate(url))
                .isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("missing core column")
                .hasMessageContaining("expires_at");
    }

    private void migrate(String url) {
        Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load().migrate();
    }

    private void createLegacySchema(String url, boolean omitExpiresAt) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE auth_account (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        user_id BIGINT,
                        email VARCHAR(255) NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        role VARCHAR(255) NOT NULL,
                        is_active BOOLEAN,
                        created_at TIMESTAMP,
                        updated_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE auth_session (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        auth_id BIGINT NOT NULL,
                        device_id VARCHAR(255) NOT NULL,
                        device_name VARCHAR(255),
                        device_type VARCHAR(255),
                        ip_address VARCHAR(255),
                        refresh_token TEXT,
                        is_active BOOLEAN,
                        last_login_at TIMESTAMP,
                        %s
                        created_at TIMESTAMP
                    )
                    """.formatted(omitExpiresAt ? "" : "expires_at TIMESTAMP,"));
        }
    }

    private long insertAccount(Statement statement, String email) throws Exception {
        statement.executeUpdate("""
                INSERT INTO auth_account (email, password_hash, role, is_active, created_at, updated_at)
                VALUES ('%s', 'hash', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(email), Statement.RETURN_GENERATED_KEYS);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertThat(keys.next()).isTrue();
            return keys.getLong(1);
        }
    }

    private void insertSession(Statement statement, long accountId, String refreshToken) throws Exception {
        if (columnExists(statement.getConnection(), "auth_session", "token_family_id")) {
            statement.executeUpdate("""
                    INSERT INTO auth_session
                        (auth_id, device_id, device_type, refresh_token, token_family_id, is_active,
                         last_login_at, expires_at, created_at)
                    VALUES (%d, 'device-1', 'mobile', '%s', '%s', true,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(accountId, refreshToken, UUID.randomUUID()));
        } else {
            statement.executeUpdate("""
                    INSERT INTO auth_session
                        (auth_id, device_id, device_type, refresh_token, is_active,
                         last_login_at, expires_at, created_at)
                    VALUES (%d, 'device-1', 'mobile', '%s', true,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(accountId, refreshToken));
        }
    }

    private boolean columnExists(Connection connection, String tableName, String expectedName) throws Exception {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(null, null, tableName, "%")) {
            while (columns.next()) {
                String name = columns.getString("COLUMN_NAME");
                if (name != null && expectedName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean indexExists(Connection connection, String tableName, String expectedName) throws Exception {
        try (ResultSet indexes = connection.getMetaData()
                .getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null && expectedName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tableExists(Connection connection, String expectedName) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if (expectedName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
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
        return "jdbc:h2:mem:auth_migration_" + label + "_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    }
}
