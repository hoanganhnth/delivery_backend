package com.delivery.livestream_service.migration;

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

class LivestreamFlywayMigrationTest {

    private static final String STREAM_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void cleanSchemaCreatesIdentityConstraintsAndQueryIndexes() throws Exception {
        String url = databaseUrl("clean");
        migrate(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertLivestream(statement, STREAM_ID, "room-1", "channel-1");
            insertProduct(statement, STREAM_ID, 10L);
            assertThatThrownBy(() -> insertLivestream(statement,
                    "22222222-2222-2222-2222-222222222222", "room-1", "channel-2"))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThatThrownBy(() -> insertProduct(statement, STREAM_ID, 10L))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThat(indexExists(connection, "livestreams", "idx_livestream_status_created")).isTrue();
            assertThat(indexExists(connection, "livestream_products", "idx_livestream_product_pinned")).isTrue();
            assertThat(indexExists(connection, "livestream_events", "idx_livestream_event_stream")).isTrue();
        }
    }

    @Test
    void legacySchemaPreservesStreamProductAndEventRows() throws Exception {
        String url = databaseUrl("legacy");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertLivestream(statement, STREAM_ID, "room-1", "channel-1");
            insertProduct(statement, STREAM_ID, 10L);
            insertEvent(statement, STREAM_ID);
        }
        migrate(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT count(*) FROM livestreams")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM livestream_products")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM livestream_events")).isEqualTo(1);
        }
    }

    @Test
    void duplicateLegacyProductScopeStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_product");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertLivestream(statement, STREAM_ID, "room-1", "channel-1");
            insertProduct(statement, STREAM_ID, 10L);
            insertProduct(statement, STREAM_ID, 10L);
        }
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate livestream product scope");
    }

    @Test
    void missingCoreColumnStopsMigration() throws Exception {
        String url = databaseUrl("missing_core");
        createLegacySchema(url, true);
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("missing core column").hasMessageContaining("updated_at");
    }

    @Test
    void emptyLegacySchemaMissingOnlyViewCountIsRepaired() throws Exception {
        String url = databaseUrl("missing_empty_view_count");
        createLegacySchema(url, false, true);

        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(columnExists(connection, "livestreams", "view_count")).isTrue();
            insertLivestreamWithoutViewCount(statement, STREAM_ID, "room-1", "channel-1");
            assertThat(count(statement, "SELECT count(*) FROM livestreams WHERE view_count = 0"))
                    .isEqualTo(1);
        }
    }

    @Test
    void populatedLegacySchemaMissingViewCountStillStopsMigration() throws Exception {
        String url = databaseUrl("missing_populated_view_count");
        createLegacySchema(url, false, true);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertLivestreamWithoutViewCount(statement, STREAM_ID, "room-1", "channel-1");
        }

        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("missing core column").hasMessageContaining("view_count");
    }

    @Test
    void incompleteTableSetStopsMigration() throws Exception {
        String url = databaseUrl("incomplete");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            createLivestreamTable(statement, false);
        }
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("schema is incomplete");
    }

    private void migrate(String url) {
        Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("0"))
                .load().migrate();
    }

    private void createLegacySchema(String url, boolean omitUpdatedAt) throws Exception {
        createLegacySchema(url, omitUpdatedAt, false);
    }

    private void createLegacySchema(String url, boolean omitUpdatedAt, boolean omitViewCount) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            createLivestreamTable(statement, omitUpdatedAt, omitViewCount);
            statement.execute("""
                    CREATE TABLE livestream_products (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, livestream_id UUID NOT NULL,
                        product_id BIGINT NOT NULL, product_name VARCHAR(255), product_image TEXT,
                        restaurant_id BIGINT, restaurant_name VARCHAR(255), price_at_live DECIMAL(12,2),
                        is_pinned BOOLEAN NOT NULL, created_at TIMESTAMP NOT NULL, pinned_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE livestream_events (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, livestream_id UUID NOT NULL,
                        type VARCHAR(255) NOT NULL, payload TEXT, created_at TIMESTAMP NOT NULL
                    )
                    """);
        }
    }

    private void createLivestreamTable(Statement statement, boolean omitUpdatedAt) throws Exception {
        createLivestreamTable(statement, omitUpdatedAt, false);
    }

    private void createLivestreamTable(Statement statement, boolean omitUpdatedAt, boolean omitViewCount)
            throws Exception {
        String viewCount = omitViewCount ? "" : "view_count BIGINT NOT NULL,";
        String updatedAt = omitUpdatedAt ? "" : ", updated_at TIMESTAMP NOT NULL";
        statement.execute("""
                CREATE TABLE livestreams (
                    id UUID PRIMARY KEY, seller_id BIGINT NOT NULL, restaurant_id BIGINT NOT NULL,
                    title VARCHAR(255) NOT NULL, description TEXT, status VARCHAR(255) NOT NULL,
                    stream_provider VARCHAR(255) NOT NULL, room_id VARCHAR(255) NOT NULL,
                    channel_name VARCHAR(255) NOT NULL, started_at TIMESTAMP, ended_at TIMESTAMP,
                    %s
                    created_at TIMESTAMP NOT NULL%s
                )
                """.formatted(viewCount, updatedAt));
    }

    private void insertLivestream(Statement statement, String id, String room, String channel) throws Exception {
        statement.executeUpdate("""
                INSERT INTO livestreams
                    (id, seller_id, restaurant_id, title, status, stream_provider, room_id,
                     channel_name, view_count, created_at, updated_at)
                VALUES ('%s', 1, 2, 'Live', 'CREATED', 'AGORA', '%s', '%s', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(id, room, channel));
    }

    private void insertLivestreamWithoutViewCount(Statement statement, String id, String room, String channel)
            throws Exception {
        statement.executeUpdate("""
                INSERT INTO livestreams
                    (id, seller_id, restaurant_id, title, status, stream_provider, room_id,
                     channel_name, created_at, updated_at)
                VALUES ('%s', 1, 2, 'Live', 'CREATED', 'AGORA', '%s', '%s',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(id, room, channel));
    }

    private void insertProduct(Statement statement, String streamId, long productId) throws Exception {
        statement.executeUpdate("""
                INSERT INTO livestream_products
                    (livestream_id, product_id, is_pinned, created_at)
                VALUES ('%s', %d, false, CURRENT_TIMESTAMP)
                """.formatted(streamId, productId));
    }

    private void insertEvent(Statement statement, String streamId) throws Exception {
        statement.executeUpdate("""
                INSERT INTO livestream_events (livestream_id, type, created_at)
                VALUES ('%s', 'LIVESTREAM_STARTED', CURRENT_TIMESTAMP)
                """.formatted(streamId));
    }

    private boolean indexExists(Connection connection, String table, String expected) throws Exception {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                null, connection.getSchema(), table, false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null && expected.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    private boolean columnExists(Connection connection, String table, String expected) throws Exception {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(null, connection.getSchema(), table, expected)) {
            return columns.next();
        }
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String databaseUrl(String label) {
        return "jdbc:h2:mem:livestream_migration_" + label + "_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    }
}
