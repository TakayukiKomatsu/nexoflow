package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Aligns every financial actor snapshot with the identity email domain. */
public class V22__widen_actor_identity_columns extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        boolean postgres = connection.getMetaData()
                .getDatabaseProductName()
                .toLowerCase()
                .contains("postgres");
        List<String[]> columns = List.of(
                new String[] {"assignors", "created_by"},
                new String[] {"receivables", "created_by"},
                new String[] {"pricing_quotes", "created_by"},
                new String[] {"settlements", "created_by"},
                new String[] {"idempotency_records", "actor"},
                new String[] {"settlement_reversals", "reversed_by"},
                new String[] {"audit_events", "actor"},
                new String[] {"base_rate_versions", "created_by"},
                new String[] {"product_spread_versions", "created_by"});
        try (Statement statement = connection.createStatement()) {
            for (String[] column : columns) {
                String alteration = postgres
                        ? " alter column " + column[1] + " type varchar(320)"
                        : " alter column " + column[1] + " varchar(320)";
                statement.execute("alter table " + column[0] + alteration);
            }
        }
    }
}
