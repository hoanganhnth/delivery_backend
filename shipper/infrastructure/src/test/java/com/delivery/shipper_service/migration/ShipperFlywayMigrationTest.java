package com.delivery.shipper_service.migration;

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

class ShipperFlywayMigrationTest {

    @Test
    void cleanSchemaCreatesIdentityLocationRatingConstraintsAndIndexes() throws Exception {
        String url = databaseUrl("clean");
        migrate(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long shipperId = insertShipper(statement, 10, "LICENSE-1", "CARD-1");
            insertLocation(statement, shipperId);
            insertRating(statement, shipperId, 100);
            assertThatThrownBy(() -> insertShipper(statement, 10, "LICENSE-2", "CARD-2"))
                    .isInstanceOf(Exception.class).hasMessageContaining("Unique");
            assertThat(indexExists(connection, "idx_shipper_online")).isTrue();
            assertThat(indexExists(connection, "idx_shipper_ratings_shipper_created")).isTrue();
        }
    }

    @Test
    void legacySchemaPreservesFleetRows() throws Exception {
        String url = databaseUrl("legacy");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long shipperId = insertShipper(statement, 10, "LICENSE-1", "CARD-1");
            insertLocation(statement, shipperId);
            insertRating(statement, shipperId, 100);
        }
        migrate(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT count(*) FROM shipper")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM shipper_locations")).isEqualTo(1);
            assertThat(count(statement, "SELECT count(*) FROM shipper_ratings")).isEqualTo(1);
        }
    }

    @Test
    void legacySchemaMissingFullNameIsRepairedFromBaselineZero() throws Exception {
        String url = databaseUrl("legacy_missing_full_name");
        createLegacySchema(url, false, true);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertShipper(statement, 10, "LICENSE-1", "CARD-1");
        }

        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnExists(connection, "shipper", "full_name")).isTrue();
            assertThat(indexExists(connection, "idx_shipper_online")).isTrue();
        }
    }

    @Test
    void versionTwoSchemaMissingFullNameIsRepairedByVersionThree() throws Exception {
        String url = databaseUrl("version_two_missing_full_name");
        createLegacySchema(url, false, true);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertShipper(statement, 10, "LICENSE-1", "CARD-1");
        }

        migrateFromBaseline(url, "2");

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnExists(connection, "shipper", "full_name")).isTrue();
            assertThat(indexExists(connection, "idx_shipper_online")).isTrue();
            assertThat(indexExists(connection, "idx_shipper_ratings_shipper_created")).isTrue();
        }
    }

    @Test
    void legacySyntheticRatingIsClearedOnlyWhenNoRatingOwnsIt() throws Exception {
        String url = databaseUrl("rating_truth");
        createLegacySchema(url, false);
        long unratedShipperId;
        long ratedShipperId;
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            unratedShipperId = insertShipper(statement, 10, "LICENSE-1", "CARD-1");
            ratedShipperId = insertShipper(statement, 11, "LICENSE-2", "CARD-2");
            insertRating(statement, ratedShipperId, 100);
        }

        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(nullableRating(statement, unratedShipperId)).isNull();
            assertThat(nullableRating(statement, ratedShipperId)).isEqualTo("5.0");
        }
    }

    @Test
    void duplicateLegacyIdentityStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_identity");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertShipper(statement, 10, "LICENSE-1", "CARD-1");
            insertShipper(statement, 11, "LICENSE-1", "CARD-2");
        }
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate license_number");
    }

    @Test
    void duplicateLegacyCurrentLocationStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_location");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long shipperId = insertShipper(statement, 10, "LICENSE-1", "CARD-1");
            insertLocation(statement, shipperId);
            insertLocation(statement, shipperId);
        }
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate location shipper_id");
    }

    @Test
    void duplicateLegacyRatingOrderStopsMigration() throws Exception {
        String url = databaseUrl("duplicate_rating");
        createLegacySchema(url, false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            long shipperId = insertShipper(statement, 10, "LICENSE-1", "CARD-1");
            insertRating(statement, shipperId, 100);
            insertRating(statement, shipperId, 100);
        }
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("duplicate rating order_id");
    }

    @Test
    void missingCoreColumnStopsMigration() throws Exception {
        String url = databaseUrl("missing_core");
        createLegacySchema(url, true);
        assertThatThrownBy(() -> migrate(url)).isInstanceOf(FlywayException.class).rootCause()
                .hasMessageContaining("missing core column").hasMessageContaining("updated_at");
    }

    private void migrate(String url) {
        migrateFromBaseline(url, "0");
    }

    private void migrateFromBaseline(String url, String version) {
        Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion(version))
                .load().migrate();
    }

    private void createLegacySchema(String url, boolean omitLocationUpdatedAt) throws Exception {
        createLegacySchema(url, omitLocationUpdatedAt, false);
    }

    private void createLegacySchema(String url, boolean omitLocationUpdatedAt, boolean omitFullName)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE shipper (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        user_id BIGINT NOT NULL, %s vehicle_type VARCHAR(50),
                        license_number VARCHAR(50), id_card VARCHAR(20), phone VARCHAR(15),
                        driver_image TEXT, id_card_front_image TEXT, id_card_back_image TEXT,
                        license_image TEXT, license_plate VARCHAR(20), is_online BOOLEAN,
                        rating DECIMAL(2,1), completed_deliveries INTEGER,
                        created_at TIMESTAMP, updated_at TIMESTAMP
                    )
                    """.formatted(omitFullName ? "" : "full_name VARCHAR(100),"));
            statement.execute("""
                    CREATE TABLE shipper_locations (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        shipper_id BIGINT NOT NULL, lat DOUBLE PRECISION NOT NULL,
                        lng DOUBLE PRECISION NOT NULL %s
                    )
                    """.formatted(omitLocationUpdatedAt ? "" : ", updated_at TIMESTAMP"));
            statement.execute("""
                    CREATE TABLE shipper_ratings (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        shipper_id BIGINT NOT NULL, customer_id BIGINT NOT NULL,
                        order_id BIGINT NOT NULL, rating INTEGER NOT NULL,
                        comment TEXT, created_at TIMESTAMP
                    )
                    """);
        }
    }

    private long insertShipper(Statement statement, long userId, String license, String idCard)
            throws Exception {
        statement.executeUpdate("""
                INSERT INTO shipper
                    (user_id, license_number, id_card, is_online, rating, completed_deliveries,
                     created_at, updated_at)
                VALUES (%d, '%s', '%s', false, 5.0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(userId, license, idCard), Statement.RETURN_GENERATED_KEYS);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertThat(keys.next()).isTrue();
            return keys.getLong(1);
        }
    }

    private void insertLocation(Statement statement, long shipperId) throws Exception {
        statement.executeUpdate("""
                INSERT INTO shipper_locations (shipper_id, lat, lng, updated_at)
                VALUES (%d, 10.75, 106.67, CURRENT_TIMESTAMP)
                """.formatted(shipperId));
    }

    private void insertRating(Statement statement, long shipperId, long orderId) throws Exception {
        statement.executeUpdate("""
                INSERT INTO shipper_ratings
                    (shipper_id, customer_id, order_id, rating, created_at)
                VALUES (%d, 20, %d, 5, CURRENT_TIMESTAMP)
                """.formatted(shipperId, orderId));
    }

    private boolean indexExists(Connection connection, String expectedName) throws Exception {
        for (String table : new String[]{"shipper", "shipper_ratings"}) {
            try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                    null, connection.getSchema(), table, false, false)) {
                while (indexes.next()) {
                    String name = indexes.getString("INDEX_NAME");
                    if (name != null && expectedName.equalsIgnoreCase(name)) return true;
                }
            }
        }
        return false;
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (ResultSet columns = connection.getMetaData().getColumns(
                null, connection.getSchema(), table, column)) {
            return columns.next();
        }
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String nullableRating(Statement statement, long shipperId) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT rating FROM shipper WHERE id = " + shipperId)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getBigDecimal(1) == null
                    ? null
                    : resultSet.getBigDecimal(1).toPlainString();
        }
    }

    private String databaseUrl(String label) {
        return "jdbc:h2:mem:shipper_migration_" + label + "_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    }
}
