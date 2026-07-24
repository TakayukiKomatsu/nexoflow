package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Makes persisted instants timezone-safe and constrains idempotency records to
 * their single legal state transition. V1-V22 persisted {@code Instant} values
 * as timezone-less JDBC timestamps, so PostgreSQL stored wall time in the
 * pgjdbc session zone. The upgrade therefore derives the legacy zone from the
 * JVM default, with an explicit system-property/environment override for hosts
 * whose zone changed, and sets that zone on the migration transaction before
 * interpreting application-authored wall values. The six immutable reference
 * rate rows authored by Flyway from explicit UTC literals are identified by
 * their stable IDs and retain UTC provenance.
 */
public class V23__harden_time_and_idempotency_invariants extends BaseJavaMigration {
    private static final String LEGACY_TIME_ZONE_PROPERTY =
            "srm.migration.v23.legacy-time-zone";
    private static final String LEGACY_TIME_ZONE_ENV =
            "SRM_MIGRATION_V23_LEGACY_TIME_ZONE";
    private static final List<String[]> INSTANT_COLUMNS = List.of(
            new String[] {"schema_metadata", "created_at"},
            new String[] {"base_rate_versions", "effective_at"},
            new String[] {"product_spread_versions", "effective_at"},
            new String[] {"exchange_rates", "observed_at"},
            new String[] {"exchange_rates", "created_at"},
            new String[] {"runtime_fixture_records", "loaded_at"},
            new String[] {"assignors", "created_at"},
            new String[] {"receivables", "created_at"},
            new String[] {"pricing_quotes", "pricing_at"},
            new String[] {"pricing_quotes", "expires_at"},
            new String[] {"pricing_quotes", "fx_observed_at"},
            new String[] {"settlements", "created_at"},
            new String[] {"idempotency_records", "created_at"},
            new String[] {"idempotency_records", "completed_at"},
            new String[] {"settlement_reversals", "reversed_at"},
            new String[] {"audit_events", "occurred_at"});

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        String database = connection.getMetaData().getDatabaseProductName().toLowerCase();
        try (Statement statement = connection.createStatement()) {
            if (database.contains("postgres")) {
                configureLegacyTimeZone(connection);
                migratePostgres(statement);
            } else if (database.contains("h2")) {
                migrateH2(statement);
            }
        }
    }

    private static void configureLegacyTimeZone(Connection connection) throws SQLException {
        String configured = System.getProperty(LEGACY_TIME_ZONE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(LEGACY_TIME_ZONE_ENV);
        }
        String candidate =
                configured == null || configured.isBlank() ? ZoneId.systemDefault().getId() : configured.strip();
        final String legacyTimeZone;
        try {
            legacyTimeZone = ZoneId.of(candidate).getId();
        } catch (DateTimeException exception) {
            throw new SQLException(
                    "Invalid V23 legacy timestamp time zone: " + candidate,
                    exception);
        }
        try (PreparedStatement statement =
                connection.prepareStatement("select set_config('TimeZone', ?, true)")) {
            statement.setString(1, legacyTimeZone);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !legacyTimeZone.equals(result.getString(1))) {
                    throw new SQLException(
                            "PostgreSQL did not apply the V23 legacy timestamp time zone");
                }
            }
        }
    }

    private static void migratePostgres(Statement statement) throws SQLException {
        statement.execute("""
                alter table schema_metadata alter column created_at
                type timestamp with time zone using created_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table base_rate_versions alter column effective_at
                type timestamp with time zone using effective_at at time zone (
                    case when id in (
                        '00000000-0000-0000-0000-000000000103',
                        '00000000-0000-0000-0000-000000000104'
                    ) then 'UTC' else current_setting('TimeZone') end
                )
                """);
        statement.execute("""
                alter table product_spread_versions alter column effective_at
                type timestamp with time zone using effective_at at time zone (
                    case when id in (
                        '00000000-0000-0000-0000-000000000101',
                        '00000000-0000-0000-0000-000000000102',
                        '00000000-0000-0000-0000-000000000105',
                        '00000000-0000-0000-0000-000000000106'
                    ) then 'UTC' else current_setting('TimeZone') end
                )
                """);
        statement.execute("""
                alter table exchange_rates alter column observed_at
                type timestamp with time zone using observed_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table exchange_rates alter column created_at
                type timestamp with time zone using created_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table runtime_fixture_records alter column loaded_at
                type timestamp with time zone using loaded_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table assignors alter column created_at
                type timestamp with time zone using created_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table receivables alter column created_at
                type timestamp with time zone using created_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table pricing_quotes alter column pricing_at
                type timestamp with time zone using pricing_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table pricing_quotes alter column expires_at
                type timestamp with time zone using expires_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table pricing_quotes alter column fx_observed_at
                type timestamp with time zone using fx_observed_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table settlements alter column created_at
                type timestamp with time zone using created_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table idempotency_records alter column created_at
                type timestamp with time zone using created_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table idempotency_records alter column completed_at
                type timestamp with time zone using completed_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table settlement_reversals alter column reversed_at
                type timestamp with time zone using reversed_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                alter table audit_events alter column occurred_at
                type timestamp with time zone using occurred_at at time zone current_setting('TimeZone')
                """);
        statement.execute("""
                create function protect_idempotency_record_transition()
                returns trigger language plpgsql as $$
                begin
                    if tg_op = 'DELETE' then
                        if old.status <> 'COMPLETED' then
                            raise exception 'processing idempotency records cannot be deleted';
                        end if;
                        return old;
                    end if;
                    if old.status = 'COMPLETED' then
                        raise exception 'completed idempotency records are immutable';
                    end if;
                    if old.status <> 'PROCESSING' or new.status <> 'COMPLETED' then
                        raise exception 'idempotency records only permit PROCESSING to COMPLETED';
                    end if;
                    if new.id is distinct from old.id
                       or new.actor is distinct from old.actor
                       or new.operation is distinct from old.operation
                       or new.idempotency_key is distinct from old.idempotency_key
                       or new.request_hash is distinct from old.request_hash
                       or new.created_at is distinct from old.created_at then
                        raise exception 'idempotency request identity is immutable';
                    end if;
                    return new;
                end;
                $$
                """);
        statement.execute("""
                create trigger idempotency_records_transition_guard
                before update or delete on idempotency_records
                for each row execute function protect_idempotency_record_transition()
                """);
    }

    private static void migrateH2(Statement statement) throws SQLException {
        statement.executeUpdate("set time zone 'UTC'");
        for (String[] column : INSTANT_COLUMNS) {
            statement.executeUpdate(
                    "alter table " + column[0] + " alter column " + column[1]
                            + " timestamp with time zone");
        }
    }
}
