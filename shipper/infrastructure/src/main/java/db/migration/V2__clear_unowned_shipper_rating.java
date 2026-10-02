package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** Removes the historical synthetic 5.0 rating only when no rating owns it. */
public class V2__clear_unowned_shipper_rating extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.executeUpdate("""
                    UPDATE shipper
                    SET rating = NULL
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM shipper_ratings
                        WHERE shipper_ratings.shipper_id = shipper.id
                    )
                    """);
        }
    }
}
