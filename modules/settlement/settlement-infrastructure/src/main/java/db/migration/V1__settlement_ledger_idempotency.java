package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Introduces the durable settlement replay boundary without guessing financial history.
 *
 * <p>This migration owns the receipt boundary and upgrades an existing ledger before
 * V2 normalizes ownership of the complete base schema. On a clean database V2 creates
 * the ledger with the same business-key constraint.</p>
 */
public class V1__settlement_ledger_idempotency extends BaseJavaMigration {

    private static final String LEDGER_CONSTRAINT = "uk_transactions_order_ledger_entry";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        createReceiptTable(connection);

        String transactionsTable = findTable(connection, "transactions");
        if (transactionsTable == null) {
            return;
        }

        requireNoDuplicateLedgerEntries(connection);
        requireNoUnreceiptedSettlements(connection);
        if (!hasIndex(connection, transactionsTable, LEDGER_CONSTRAINT)) {
            execute(connection, """
                    ALTER TABLE transactions
                    ADD CONSTRAINT uk_transactions_order_ledger_entry
                    UNIQUE (order_id, entity_id, entity_type, reason, wallet_type, direction)
                    """);
        }
    }

    private void createReceiptTable(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS settlement_receipts (
                    event_id UUID PRIMARY KEY,
                    order_id BIGINT NOT NULL,
                    delivery_id BIGINT NOT NULL,
                    payload_fingerprint VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    CONSTRAINT uk_settlement_receipts_order UNIQUE (order_id)
                )
                """);
    }

    private void requireNoDuplicateLedgerEntries(Connection connection) throws SQLException {
        long duplicateGroups = queryCount(connection, """
                SELECT count(*)
                FROM (
                    SELECT order_id, entity_id, entity_type, reason, wallet_type, direction
                    FROM transactions
                    WHERE order_id IS NOT NULL
                    GROUP BY order_id, entity_id, entity_type, reason, wallet_type, direction
                    HAVING count(*) > 1
                ) duplicate_entries
                """);
        if (duplicateGroups > 0) {
            throw new FlywayException("Settlement ledger contains " + duplicateGroups
                    + " duplicate business-key group(s); manual reconciliation is required");
        }
    }

    private void requireNoUnreceiptedSettlements(Connection connection) throws SQLException {
        long legacySettlements = queryCount(connection, """
                SELECT count(*)
                FROM transactions transaction_entry
                WHERE transaction_entry.order_id IS NOT NULL
                  AND transaction_entry.entity_id = 0
                  AND transaction_entry.entity_type = 'SYSTEM'
                  AND transaction_entry.reason = 'PLATFORM_COMMISSION'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM settlement_receipts receipt
                      WHERE receipt.order_id = transaction_entry.order_id
                  )
                """);
        if (legacySettlements > 0) {
            throw new FlywayException("Settlement ledger contains " + legacySettlements
                    + " completed order(s) without durable receipts; reconcile event identity before migration");
        }
    }

    private long queryCount(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String findTable(Connection connection, String expectedName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (expectedName.equalsIgnoreCase(tableName)) {
                    return tableName;
                }
            }
        }
        return null;
    }

    private boolean hasIndex(Connection connection, String tableName, String expectedName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(null, null, tableName, true, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null && expectedName.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
