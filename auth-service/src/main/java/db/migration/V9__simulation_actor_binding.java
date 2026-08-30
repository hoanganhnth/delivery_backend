package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Server-owned binding that makes a test actor eligible for one simulation run. */
public class V9__simulation_actor_binding extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (var statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE auth_account ADD COLUMN IF NOT EXISTS simulation_actor BOOLEAN NOT NULL DEFAULT FALSE");
            statement.execute("ALTER TABLE auth_account ADD COLUMN IF NOT EXISTS simulation_cohort_id UUID");
            statement.execute("ALTER TABLE auth_account ADD COLUMN IF NOT EXISTS active_simulation_run_id UUID");
            statement.execute("ALTER TABLE auth_account ADD COLUMN IF NOT EXISTS simulation_binding_version BIGINT NOT NULL DEFAULT 0");
            // A regular index is portable to the H2 Flyway validation suite;
            // PostgreSQL also indexes NULL values cheaply enough for this
            // sparse operator-only lookup.
            statement.execute("CREATE INDEX IF NOT EXISTS idx_auth_account_simulation_run "
                    + "ON auth_account (active_simulation_run_id)");
        }
    }
}
