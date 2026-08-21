package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Existing lifecycle rows predate versioned status events. Version one is their
 * authoritative baseline; the receipt table makes the later bootstrap publish
 * idempotent without deleting or rewriting legacy User status-sync data.
 */
public class V7__identity_status_bootstrap extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        execute(connection, "UPDATE auth_account SET lifecycle_version = 1 "
                + "WHERE lifecycle_version IS NULL OR lifecycle_version = 0");
        execute(connection, "CREATE TABLE IF NOT EXISTS identity_status_bootstrap ("
                + "auth_account_id BIGINT PRIMARY KEY,lifecycle_version BIGINT NOT NULL,emitted_at TIMESTAMP NOT NULL)");
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
}
