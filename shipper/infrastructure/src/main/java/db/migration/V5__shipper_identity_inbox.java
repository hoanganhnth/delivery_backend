package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V5__shipper_identity_inbox extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE shipper ADD COLUMN IF NOT EXISTS identity_status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE'");
            statement.execute("ALTER TABLE shipper ADD COLUMN IF NOT EXISTS identity_status_version BIGINT NOT NULL DEFAULT 0");
            statement.execute("CREATE TABLE IF NOT EXISTS identity_inbox_receipts ("
                    + "event_id UUID PRIMARY KEY,event_type VARCHAR(128) NOT NULL,principal_id BIGINT NOT NULL,"
                    + "payload_fingerprint VARCHAR(64) NOT NULL,processed_at TIMESTAMP NOT NULL)");
        }
    }
}
