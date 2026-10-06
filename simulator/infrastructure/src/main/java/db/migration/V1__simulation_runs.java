package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V1__simulation_runs extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        try (var s = context.getConnection().createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS simulation_runs (run_id UUID PRIMARY KEY, status VARCHAR(32) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL, scenario_json TEXT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_simulation_runs_expiry ON simulation_runs (expires_at, status)");
            s.execute("CREATE TABLE IF NOT EXISTS simulation_actor_leases (lease_id UUID PRIMARY KEY, run_id UUID NOT NULL, principal_id BIGINT NOT NULL, fencing_token BIGINT NOT NULL, lease_expires_at TIMESTAMP WITH TIME ZONE NOT NULL, status VARCHAR(16) NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_simulation_actor_lease_principal ON simulation_actor_leases (principal_id, status)");
            s.execute("CREATE TABLE IF NOT EXISTS simulation_ledger_entries (event_id UUID PRIMARY KEY, run_id UUID NOT NULL, order_id BIGINT NOT NULL, delivery_id BIGINT NOT NULL, total_price DECIMAL(14,2) NOT NULL, recorded_at TIMESTAMP WITH TIME ZONE NOT NULL)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_simulation_ledger_delivery ON simulation_ledger_entries (delivery_id)");
        }
    }
}
