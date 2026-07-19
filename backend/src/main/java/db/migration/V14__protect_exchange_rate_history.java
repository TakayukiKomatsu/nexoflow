package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** PostgreSQL guard making exchange-rate history append-only; H2 has no PL/pgSQL trigger support. */
public class V14__protect_exchange_rate_history extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create or replace function protect_exchange_rate_history()
                    returns trigger language plpgsql as $$
                    begin
                        raise exception 'exchange_rates rows are immutable';
                    end;
                    $$
                    """);
            statement.execute(
                    "create trigger exchange_rates_immutable before update or delete on exchange_rates "
                            + "for each row execute function protect_exchange_rate_history()");
        }
    }
}
