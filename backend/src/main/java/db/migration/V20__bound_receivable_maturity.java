package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Keeps persisted Receivable maturities inside the ten-year pricing domain. */
public class V20__bound_receivable_maturity extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        boolean postgres = connection.getMetaData()
                .getDatabaseProductName()
                .toLowerCase()
                .contains("postgres");
        String maximumDueDate = postgres
                ? "issue_date + interval '10 years'"
                : "dateadd(year, 10, issue_date)";
        String maximumPricingDueDate = postgres
                ? "cast(pricing_at as date) + interval '10 years'"
                : "dateadd(year, 10, cast(pricing_at as date))";
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "alter table receivables add constraint receivables_maximum_maturity "
                            + "check (due_date <= " + maximumDueDate + ")");
            statement.execute(
                    "alter table pricing_quotes add constraint pricing_quotes_maximum_term "
                            + "check (due_date <= " + maximumPricingDueDate + ")");
        }
    }
}
