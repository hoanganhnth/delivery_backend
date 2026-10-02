package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Auth identity treats email as a case-insensitive login/registration key.
 * Refuse historical case-only collisions rather than choosing an account or
 * deleting data during an additive rollout.
 */
public class V8__canonical_auth_account_email extends BaseJavaMigration {
    private static final String INDEX = "uk_auth_account_email_canonical";

    @Override
    public boolean canExecuteInTransaction() {
        // PostgreSQL prohibits CREATE INDEX CONCURRENTLY inside a transaction.
        // This avoids a long ACCESS EXCLUSIVE-style registration outage while
        // the large Auth table is indexed during additive T0 deployment.
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        long collisions = caseInsensitiveCollisions(connection);
        if (collisions > 0) {
            throw new IllegalStateException(
                    "Cannot enforce canonical auth email: " + collisions
                            + " case/whitespace-insensitive collision group(s) exist in auth_account. "
                            + "Remediate the duplicate identities before retrying migration.");
        }
        try (Statement statement = connection.createStatement()) {
            if (!isPostgreSql(connection)) {
                // H2 is the schema-validation/test equivalent. It has no pg_index
                // catalog or expression indexes, and does not support PostgreSQL's
                // CONCURRENTLY syntax. A generated canonical column preserves the
                // same lower+trim uniqueness invariant for H2-only proof.
                statement.execute("ALTER TABLE auth_account ADD COLUMN canonical_email "
                        + "VARCHAR(255) GENERATED ALWAYS AS (lower(trim(email)))");
                statement.execute("CREATE UNIQUE INDEX " + INDEX
                        + " ON auth_account (canonical_email)");
                return;
            }

            // A cancelled concurrent build leaves an invalid catalog index.
            // IF NOT EXISTS would otherwise skip it forever on the next
            // operator retry, so remove only that known invalid artifact.
            if (hasInvalidIndex(connection)) {
                statement.execute("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX);
            }
            statement.execute("CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS " + INDEX + " "
                    + "ON auth_account (lower(btrim(email)))");
        }
    }

    private static boolean isPostgreSql(Connection connection) throws Exception {
        return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    }

    private static long caseInsensitiveCollisions(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT count(*) FROM ("
                                + "SELECT lower(btrim(email)) FROM auth_account "
                                + "GROUP BY lower(btrim(email)) HAVING count(*) > 1"
                                + ") collisions")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static boolean hasInvalidIndex(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT NOT index.indisvalid FROM pg_index index "
                        + "JOIN pg_class relation ON relation.oid = index.indexrelid "
                        + "WHERE relation.relname = ? AND index.indrelid = 'auth_account'::regclass")) {
            statement.setString(1, INDEX);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
