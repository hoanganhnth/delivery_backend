package com.delivery.settlement_service.migration;

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

class SettlementFlywayMigrationTest {

    @Test
    void cleanSchemaCreatesDurableReceiptTable() throws Exception {
        String url = databaseUrl("clean");

        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO settlement_receipts
                        (event_id, order_id, delivery_id, payload_fingerprint, created_at)
                    VALUES
                        ('11111111-1111-1111-1111-111111111111', 10, 20,
                         'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', CURRENT_TIMESTAMP)
                    """);
            assertThat(count(statement, "SELECT count(*) FROM settlement_receipts")).isEqualTo(1);
        }
    }

    @Test
    void existingSchemaReceivesLedgerBusinessKeyConstraint() throws Exception {
        String url = databaseUrl("existing");
        createLegacyTransactionsTable(url);
        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertLedgerEntry(statement, 10, 20, "SHIPPER", "COD_SETTLEMENT", "DEPOSIT", "DEBIT");
            assertThatThrownBy(() ->
                    insertLedgerEntry(statement, 10, 20, "SHIPPER", "COD_SETTLEMENT", "DEPOSIT", "DEBIT"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("Unique");
        }
    }

    @Test
    void duplicateLegacyLedgerStopsMigration() throws Exception {
        String url = databaseUrl("duplicate");
        createLegacyTransactionsTable(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertLedgerEntry(statement, 10, 20, "SHIPPER", "COD_SETTLEMENT", "DEPOSIT", "DEBIT");
            insertLedgerEntry(statement, 10, 20, "SHIPPER", "COD_SETTLEMENT", "DEPOSIT", "DEBIT");
        }

        assertThatThrownBy(() -> migrate(url))
                .isInstanceOf(FlywayException.class)
                .rootCause()
                .hasMessageContaining("duplicate business-key");
    }

    @Test
    void unreceiptedCompletedSettlementStopsMigration() throws Exception {
        String url = databaseUrl("unreceipted");
        createLegacyTransactionsTable(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertLedgerEntry(statement, 10, 0, "SYSTEM", "PLATFORM_COMMISSION", "EARNINGS", "CREDIT");
        }

        assertThatThrownBy(() -> migrate(url))
                .isInstanceOf(FlywayException.class)
                .rootCause()
                .hasMessageContaining("without durable receipts");
    }

    @Test
    void existingReceiptAllowsManagedMigrationOfCompletedSettlement() throws Exception {
        String url = databaseUrl("receipted");
        createLegacyTransactionsTable(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE settlement_receipts (
                        event_id UUID PRIMARY KEY,
                        order_id BIGINT NOT NULL UNIQUE,
                        delivery_id BIGINT NOT NULL,
                        payload_fingerprint VARCHAR(64) NOT NULL,
                        created_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO settlement_receipts
                        (event_id, order_id, delivery_id, payload_fingerprint, created_at)
                    VALUES
                        ('22222222-2222-2222-2222-222222222222', 10, 30,
                         'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', CURRENT_TIMESTAMP)
                    """);
            insertLedgerEntry(statement, 10, 0, "SYSTEM", "PLATFORM_COMMISSION", "EARNINGS", "CREDIT");
        }

        migrate(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT count(*) FROM settlement_receipts")).isEqualTo(1);
        }
    }

    private void migrate(String url) {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();
    }

    private void createLegacyTransactionsTable(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE transactions (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        order_id BIGINT,
                        entity_id BIGINT NOT NULL,
                        entity_type VARCHAR(20) NOT NULL,
                        reason VARCHAR(40) NOT NULL,
                        wallet_type VARCHAR(20) NOT NULL,
                        direction VARCHAR(10) NOT NULL
                    )
                    """);
        }
    }

    private void insertLedgerEntry(
            Statement statement,
            long orderId,
            long entityId,
            String entityType,
            String reason,
            String walletType,
            String direction) throws Exception {
        statement.executeUpdate("""
                INSERT INTO transactions
                    (order_id, entity_id, entity_type, reason, wallet_type, direction)
                VALUES (%d, %d, '%s', '%s', '%s', '%s')
                """.formatted(orderId, entityId, entityType, reason, walletType, direction));
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String databaseUrl(String label) {
        return "jdbc:h2:mem:settlement_migration_" + label + "_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    }
}
