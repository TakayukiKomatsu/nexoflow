package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** PostgreSQL guard making the audit_events ledger append-only; H2 has no PL/pgSQL trigger support. */
public class V13__enforce_audit_event_immutability extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "create trigger audit_events_immutable before update or delete on audit_events "
                            + "for each row execute function protect_completed_financial_rows()");
        }
    }
}
