package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** PostgreSQL guard making settlement reversal history append-only; H2 has no PL/pgSQL trigger support. */
public class V16__protect_settlement_reversal_history extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create or replace function protect_settlement_reversal_history()
                    returns trigger language plpgsql as $$
                    begin
                        raise exception 'settlement_reversals rows are immutable';
                    end;
                    $$
                    """);
            statement.execute(
                    "create trigger settlement_reversals_immutable before update or delete on settlement_reversals "
                            + "for each row execute function protect_settlement_reversal_history()");
        }
    }
}
