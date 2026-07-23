package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** PostgreSQL guard making effective-dated pricing reference history append-only. */
public class V19__protect_reference_rate_history extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create or replace function protect_reference_rate_history()
                    returns trigger language plpgsql as $$
                    begin
                        raise exception '% rows are immutable', tg_table_name;
                    end;
                    $$
                    """);
            statement.execute(
                    "create trigger base_rate_versions_immutable before update or delete on base_rate_versions "
                            + "for each row execute function protect_reference_rate_history()");
            statement.execute(
                    "create trigger product_spread_versions_immutable before update or delete on product_spread_versions "
                            + "for each row execute function protect_reference_rate_history()");
        }
    }
}
