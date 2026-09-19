package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Enforces the one-default-address-per-user invariant on PostgreSQL. */
public class V4__user_address_single_default extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (isH2(connection)) {
            return;
        }

        long duplicateUsers = duplicateDefaultUsers(connection);
        if (duplicateUsers > 0) {
            throw new FlywayException("User schema contains " + duplicateUsers
                    + " user(s) with multiple default addresses; manual reconciliation is required");
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uk_user_addresses_one_default
                    ON user_addresses (user_id)
                    WHERE is_default = true
                    """);
        }
    }

    private long duplicateDefaultUsers(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT count(*) FROM (
                         SELECT user_id FROM user_addresses
                         WHERE is_default = true
                         GROUP BY user_id HAVING count(*) > 1
                     ) duplicate_defaults
                     """)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private boolean isH2(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName()
                .toLowerCase(java.util.Locale.ROOT).contains("h2");
    }
}
