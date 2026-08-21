package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * `auth_id` is a legacy column name for the same Auth-owned identity as
 * `principal_id`. Do not silently rewrite a divergent existing row: it needs
 * local User-data remediation before the release can safely enforce the
 * invariant.
 */
public class V3__enforce_user_auth_principal_identity extends BaseJavaMigration {
    private static final String CONSTRAINT = "ck_users_auth_id_equals_principal_id";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        long divergent = countDivergentRows(connection);
        if (divergent > 0) {
            throw new IllegalStateException(
                    "Cannot enforce users auth/principal identity invariant: "
                            + divergent
                            + " row(s) have auth_id <> principal_id. Remediate in User DB before retrying migration.");
        }
        if (!constraintExists(connection)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE users ADD CONSTRAINT " + CONSTRAINT
                        + " CHECK (auth_id = principal_id) NOT VALID");
                // PostgreSQL validates a NOT VALID check with a weaker lock
                // than adding an immediately validated constraint. The
                // preceding count remains a clear remediation error, while
                // VALIDATE closes the race with concurrent legacy writers.
                statement.execute("ALTER TABLE users VALIDATE CONSTRAINT " + CONSTRAINT);
            }
        }
    }

    private static long countDivergentRows(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT count(*) FROM users WHERE auth_id IS DISTINCT FROM principal_id")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static boolean constraintExists(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM pg_constraint WHERE conname = ? AND conrelid = 'users'::regclass")) {
            statement.setString(1, CONSTRAINT);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
