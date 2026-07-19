package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Adds the complete quote snapshot while preserving PostgreSQL quote immutability. */
public class V15__complete_pricing_quote_snapshots extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        boolean postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");

        try (Statement statement = connection.createStatement()) {
            if (postgres) {
                statement.execute("drop trigger pricing_quotes_immutable on pricing_quotes");
            }

            statement.execute("alter table pricing_quotes add column product_type_code varchar(50)");
            statement.execute("""
                    update pricing_quotes q
                    set product_type_code = r.product_type_code
                    from receivables r
                    where r.id = q.receivable_id
                    """);
            statement.execute("alter table pricing_quotes alter column product_type_code set not null");
            statement.execute("""
                    alter table pricing_quotes
                    add constraint pricing_quotes_product_type_fk
                    foreign key (product_type_code) references product_types(code)
                    """);

            if (postgres) {
                replacePostgresImmutabilityGuard(statement);
            }
        }
    }

    private static void replacePostgresImmutabilityGuard(Statement statement) throws SQLException {
        statement.execute("""
                create or replace function protect_pricing_quote_snapshot() returns trigger language plpgsql as $$
                begin
                    if tg_op = 'DELETE' then raise exception 'pricing quotes are immutable'; end if;
                    if old.status = 'ACTIVE' and new.status = 'CONSUMED'
                       and new.id = old.id and new.receivable_id = old.receivable_id
                       and new.settlement_currency_code = old.settlement_currency_code
                       and new.face_amount = old.face_amount and new.face_currency_code = old.face_currency_code
                       and new.product_type_code = old.product_type_code
                       and new.due_date = old.due_date and new.pricing_at = old.pricing_at
                       and new.expires_at = old.expires_at and new.base_rate = old.base_rate
                       and new.spread = old.spread and new.strategy_code = old.strategy_code
                       and new.day_count_convention = old.day_count_convention
                       and new.term_in_months = old.term_in_months
                       and new.discounted_amount = old.discounted_amount
                       and new.fx_base_currency_code = old.fx_base_currency_code
                       and new.fx_quote_currency_code = old.fx_quote_currency_code
                       and new.fx_rate = old.fx_rate and new.fx_source = old.fx_source
                       and new.fx_observed_at = old.fx_observed_at
                       and new.settlement_amount = old.settlement_amount
                       and new.created_by = old.created_by then
                        return new;
                    end if;
                    raise exception 'pricing quote snapshots are immutable';
                end $$
                """);
        statement.execute("""
                create trigger pricing_quotes_immutable
                before update or delete on pricing_quotes
                for each row execute function protect_pricing_quote_snapshot()
                """);
    }
}
