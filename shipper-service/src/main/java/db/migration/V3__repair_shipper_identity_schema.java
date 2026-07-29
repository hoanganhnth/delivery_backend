package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Repairs runtime schemas that were baselined before the current Shipper V1 contract. */
public class V3__repair_shipper_identity_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String shippers = requireTable(connection, "shipper");
        String locations = requireTable(connection, "shipper_locations");
        String ratings = requireTable(connection, "shipper_ratings");

        addNullableColumnIfMissing(connection, shippers, "full_name", "full_name VARCHAR(100)");

        requireNoDuplicate(connection, "shipper", "user_id", false, "user_id");
        requireNoDuplicate(connection, "shipper", "license_number", true, "license_number");
        requireNoDuplicate(connection, "shipper", "id_card", true, "id_card");
        requireNoDuplicate(connection, "shipper_locations", "shipper_id", false, "location shipper_id");
        requireNoDuplicate(connection, "shipper_ratings", "order_id", false, "rating order_id");

        addUniqueIfMissing(connection, shippers, "user_id", "uk_shipper_user_id");
        addUniqueIfMissing(connection, shippers, "license_number", "uk_shipper_license_number");
        addUniqueIfMissing(connection, shippers, "id_card", "uk_shipper_id_card");
        addUniqueIfMissing(connection, locations, "shipper_id", "uk_shipper_locations_shipper_id");
        addUniqueIfMissing(connection, ratings, "order_id", "uk_shipper_ratings_order_id");

        execute(connection, "CREATE INDEX IF NOT EXISTS idx_shipper_online ON shipper (is_online)");
        execute(connection, """
                CREATE INDEX IF NOT EXISTS idx_shipper_ratings_shipper_created
                ON shipper_ratings (shipper_id, created_at)
                """);
    }

    private void addNullableColumnIfMissing(Connection connection, String tableName, String columnName,
                                            String definition) throws SQLException {
        if (!hasColumn(connection, tableName, columnName)) {
            execute(connection, "ALTER TABLE " + tableName + " ADD COLUMN " + definition);
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData()
                .getColumns(null, connection.getSchema(), tableName, "%")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))) return true;
            }
        }
        return false;
    }

    private void requireNoDuplicate(Connection connection, String table, String column,
                                    boolean ignoreNull, String label) throws SQLException {
        String where = ignoreNull ? " WHERE " + column + " IS NOT NULL" : "";
        long groups = queryCount(connection, "SELECT count(*) FROM (SELECT " + column + " FROM "
                + table + where + " GROUP BY " + column + " HAVING count(*) > 1) duplicates");
        if (groups > 0) {
            throw new FlywayException("Shipper schema contains " + groups + " duplicate " + label
                    + " group(s); manual reconciliation is required");
        }
    }

    private void addUniqueIfMissing(Connection connection, String tableName, String column,
                                    String constraint) throws SQLException {
        if (!hasUniqueSingleColumnIndex(connection, tableName, column)) {
            execute(connection, "ALTER TABLE " + tableName + " ADD CONSTRAINT " + constraint
                    + " UNIQUE (" + column + ")");
        }
    }

    private long queryCount(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String requireTable(Connection connection, String expectedName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(
                null, connection.getSchema(), "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (expectedName.equalsIgnoreCase(name)) return name;
            }
        }
        throw new FlywayException("Existing Shipper schema is incomplete: missing table " + expectedName);
    }

    private boolean hasUniqueSingleColumnIndex(Connection connection, String tableName, String columnName)
            throws SQLException {
        Map<String, Set<String>> columnsByIndex = new HashMap<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                null, connection.getSchema(), tableName, true, false)) {
            while (indexes.next()) {
                String index = indexes.getString("INDEX_NAME");
                String column = indexes.getString("COLUMN_NAME");
                if (index != null && column != null) {
                    columnsByIndex.computeIfAbsent(index.toLowerCase(Locale.ROOT), ignored -> new HashSet<>())
                            .add(column.toLowerCase(Locale.ROOT));
                }
            }
        }
        return columnsByIndex.values().stream().anyMatch(columns -> columns.equals(Set.of(columnName)));
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
