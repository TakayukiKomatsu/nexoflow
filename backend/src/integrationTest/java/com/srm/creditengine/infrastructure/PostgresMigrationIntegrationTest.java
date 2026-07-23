package com.srm.creditengine.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PostgresMigrationIntegrationTest {
    private static final List<String> INSTANT_COLUMNS = List.of(
            "schema_metadata.created_at",
            "base_rate_versions.effective_at",
            "product_spread_versions.effective_at",
            "exchange_rates.observed_at",
            "exchange_rates.created_at",
            "runtime_fixture_records.loaded_at",
            "assignors.created_at",
            "receivables.created_at",
            "pricing_quotes.pricing_at",
            "pricing_quotes.expires_at",
            "pricing_quotes.fx_observed_at",
            "settlements.created_at",
            "idempotency_records.created_at",
            "idempotency_records.completed_at",
            "settlement_reversals.reversed_at",
            "audit_events.occurred_at");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("srm_credit_engine")
            .withUsername("srm")
            .withPassword("srm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("srm.jwt-secret", () -> "srm-test-secret-do-not-use-32-bytes-minimum");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postgresAppliesFlywayMigrationsAndValidatesSchema() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("select count(*) from flyway_schema_history")) {
            rows.next();
            assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(6);
        }
    }

    @Test
    void auditEventsRejectsUpdateAndDelete() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into audit_events (id,actor,action,target_type,target_id,occurred_at,safe_metadata) values (?,?,?,?,?,?,?::jsonb)",
                id, "operator@srm.local", "SETTLEMENT_CREATED", "SETTLEMENT", UUID.randomUUID(), Timestamp.from(Instant.now()), "{}");

        assertThatThrownBy(() -> jdbc.update("update audit_events set actor='tampered' where id=?", id))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from audit_events where id=?", id))
                .hasMessageContaining("immutable");

        Integer count = jdbc.queryForObject("select count(*) from audit_events where id=?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void referenceRateTablesRejectValuesAboveThePricingDomainMaximum() {
        Instant effectiveAt = Instant.parse("2040-01-01T00:00:00Z");

        assertThatThrownBy(() -> jdbc.update(
                        "insert into base_rate_versions (id,currency_code,monthly_rate,effective_at,created_by) values (?,?,?,?,?)",
                        UUID.randomUUID(),
                        "BRL",
                        new BigDecimal("1.0000000001"),
                        Timestamp.from(effectiveAt),
                        "migration-test"))
                .hasMessageContaining("base_rate_versions_monthly_rate_domain");
        assertThatThrownBy(() -> jdbc.update(
                        "insert into product_spread_versions (id,product_type_code,monthly_spread,effective_at,created_by) values (?,?,?,?,?)",
                        UUID.randomUUID(),
                        "MERCANTILE_INVOICE",
                        new BigDecimal("999"),
                        Timestamp.from(effectiveAt),
                        "migration-test"))
                .hasMessageContaining("product_spread_versions_monthly_spread_domain");
    }

    @Test
    void referenceRateHistoryRejectsMutationAndDeletion() {
        UUID baseRateId = UUID.randomUUID();
        UUID spreadId = UUID.randomUUID();
        Timestamp effectiveAt = Timestamp.from(Instant.parse("2043-01-01T00:00:00Z"));
        jdbc.update(
                "insert into base_rate_versions (id,currency_code,monthly_rate,effective_at,created_by) values (?,?,?,?,?)",
                baseRateId,
                "BRL",
                new BigDecimal("0.0310000000"),
                effectiveAt,
                "migration-test");
        jdbc.update(
                "insert into product_spread_versions (id,product_type_code,monthly_spread,effective_at,created_by) values (?,?,?,?,?)",
                spreadId,
                "MERCANTILE_INVOICE",
                new BigDecimal("0.0410000000"),
                effectiveAt,
                "migration-test");

        assertThatThrownBy(() -> jdbc.update(
                        "update base_rate_versions set monthly_rate=0.99 where id=?", baseRateId))
                .hasMessageContaining("base_rate_versions rows are immutable");
        assertThatThrownBy(() -> jdbc.update(
                        "delete from product_spread_versions where id=?", spreadId))
                .hasMessageContaining("product_spread_versions rows are immutable");
    }

    @Test
    void receivableTableRejectsMaturitiesBeyondTenYears() {
        UUID assignorId = UUID.randomUUID();
        jdbc.update(
                "insert into assignors (id,legal_name,normalized_tax_id,active,created_at,created_by) values (?,?,?,?,?,?)",
                assignorId,
                "Maturity Constraint Co",
                "MATURITY-" + assignorId.toString().substring(0, 8),
                true,
                Timestamp.from(Instant.parse("2030-01-15T12:00:00Z")),
                "migration-test");

        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into receivables
                            (id,assignor_id,product_type_code,face_currency_code,face_amount,
                             issue_date,due_date,status,version,created_at,created_by)
                        values (?,?,?,?,?,?,?,?,?,?,?)
                        """,
                        UUID.randomUUID(),
                        assignorId,
                        "MERCANTILE_INVOICE",
                        "BRL",
                        new BigDecimal("1000.0000"),
                        Date.valueOf(LocalDate.parse("2030-01-15")),
                        Date.valueOf(LocalDate.parse("2040-01-16")),
                        "REGISTERED",
                        0,
                        Timestamp.from(Instant.parse("2030-01-15T12:00:00Z")),
                        "migration-test"))
                .hasMessageContaining("receivables_maximum_maturity");
        assertThat(jdbc.queryForObject(
                        "select count(*) from pg_constraint where conname in ('receivables_maximum_maturity','pricing_quotes_maximum_term')",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    void statementReadModelHasIndexesForLargeVolumeFilterBranches() {
        var indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname=current_schema()",
                String.class);

        assertThat(indexes).contains(
                "settlements_statement_filter_idx",
                "settlement_reversals_statement_filter_idx",
                "settlement_items_product_statement_idx");
    }

    @Test
    void everyFinancialActorSnapshotAcceptsTheIdentityEmailWidth() {
        Integer alignedColumns = jdbc.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where (table_name, column_name) in (
                    ('assignors','created_by'),
                    ('receivables','created_by'),
                    ('pricing_quotes','created_by'),
                    ('settlements','created_by'),
                    ('idempotency_records','actor'),
                    ('settlement_reversals','reversed_by'),
                    ('audit_events','actor'),
                    ('base_rate_versions','created_by'),
                    ('product_spread_versions','created_by'))
                  and character_maximum_length = 320
                """,
                Integer.class);

        assertThat(alignedColumns).isEqualTo(9);
    }

    @Test
    void everyPersistedInstantUsesTimeZoneAwareStorage() {
        List<Map<String, Object>> columns = jdbc.queryForList(
                """
                select table_name,column_name,data_type
                from information_schema.columns
                where table_schema=current_schema()
                  and table_name <> 'flyway_schema_history'
                  and data_type like 'timestamp%'
                order by table_name,column_name
                """);

        assertThat(columns)
                .extracting(column -> column.get("table_name") + "." + column.get("column_name"))
                .containsExactlyInAnyOrderElementsOf(INSTANT_COLUMNS);
        assertThat(columns)
                .extracting(column -> column.get("data_type"))
                .containsOnly("timestamp with time zone");
    }

    @Test
    void utcInstantsAndHalfOpenBoundariesSurviveNonUtcJvmAndSessionTimeZones()
            throws Exception {
        TimeZone originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        try (Connection connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.execute("set time zone 'America/Los_Angeles'");
            }
            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Tokyo");
            assertThat(queryString(connection, "show timezone")).isEqualTo("America/Los_Angeles");

            Instant seededUtcInstant = queryInstant(
                    connection,
                    "select effective_at from base_rate_versions "
                            + "where id='00000000-0000-0000-0000-000000000103'");
            assertThat(seededUtcInstant).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));

            String source = "timezone-" + UUID.randomUUID();
            Instant lowerBoundary = Instant.parse("2044-05-06T07:08:09Z");
            Instant upperBoundary = lowerBoundary.plusSeconds(60);
            UUID exactBoundaryId = UUID.randomUUID();
            insertExchangeRate(
                    connection, UUID.randomUUID(), source, lowerBoundary.minusSeconds(1));
            insertExchangeRate(connection, exactBoundaryId, source, lowerBoundary);
            insertExchangeRate(connection, UUID.randomUUID(), source, upperBoundary);

            assertThat(queryInstant(
                            connection,
                            "select observed_at from exchange_rates where id=?",
                            exactBoundaryId))
                    .isEqualTo(lowerBoundary);
            assertThat(countExchangeRatesInHalfOpenWindow(
                            connection, source, lowerBoundary, upperBoundary))
                    .isEqualTo(1);
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void idempotencyRecordPermitsOneCompletionThenRejectsMutationAndDeletion() {
        UUID settlementId = newSettlement();
        UUID recordId = insertProcessingIdempotencyRecord("completion");
        Instant completedAt = Instant.parse("2045-01-02T03:04:05Z");

        assertThat(jdbc.update(
                        "update idempotency_records set settlement_id=?,status='COMPLETED',"
                                + "completed_at=? where id=?",
                        settlementId,
                        Timestamp.from(completedAt),
                        recordId))
                .isEqualTo(1);
        assertThat(jdbc.queryForMap(
                        "select status,settlement_id,completed_at from idempotency_records where id=?",
                        recordId))
                .containsEntry("status", "COMPLETED")
                .containsEntry("settlement_id", settlementId);
        assertThat(jdbc.queryForObject(
                        "select completed_at from idempotency_records where id=?",
                        Timestamp.class,
                        recordId)
                        .toInstant())
                .isEqualTo(completedAt);

        assertThatThrownBy(() -> jdbc.update(
                        "update idempotency_records set completed_at=? where id=?",
                        Timestamp.from(completedAt.plusSeconds(1)),
                        recordId))
                .hasMessageContaining("completed idempotency records are immutable");
        assertThatThrownBy(() -> jdbc.update(
                        "delete from idempotency_records where id=?", recordId))
                .hasMessageContaining("idempotency records cannot be deleted");
        assertThat(jdbc.queryForObject(
                        "select count(*) from idempotency_records where id=?",
                        Integer.class,
                        recordId))
                .isEqualTo(1);
    }

    @Test
    void processingIdempotencyRecordRejectsEveryMutationExceptExactCompletion() {
        UUID noOpRecordId = insertProcessingIdempotencyRecord("no-op");
        UUID identityMutationRecordId = insertProcessingIdempotencyRecord("identity");
        UUID deletionRecordId = insertProcessingIdempotencyRecord("deletion");
        UUID settlementId = newSettlement();

        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> jdbc.update(
                            "update idempotency_records set status='PROCESSING' where id=?",
                            noOpRecordId))
                    .hasMessageContaining("only permit PROCESSING to COMPLETED");
            softly.assertThatThrownBy(() -> jdbc.update(
                            "update idempotency_records set actor=?,settlement_id=?,"
                                    + "status='COMPLETED',completed_at=? where id=?",
                            "tampered@srm.local",
                            settlementId,
                            Timestamp.from(Instant.parse("2045-01-02T03:04:05Z")),
                            identityMutationRecordId))
                    .hasMessageContaining("idempotency request identity is immutable");
            softly.assertThatThrownBy(() -> jdbc.update(
                            "delete from idempotency_records where id=?", deletionRecordId))
                    .hasMessageContaining("idempotency records cannot be deleted");
        });
        assertThat(jdbc.queryForObject(
                        "select count(*) from idempotency_records where id in (?,?,?)",
                        Integer.class,
                        noOpRecordId,
                        identityMutationRecordId,
                        deletionRecordId))
                .isEqualTo(3);
    }

    private UUID newSettlement() {
        UUID assignorId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        Timestamp createdAt = Timestamp.from(Instant.parse("2045-01-01T00:00:00Z"));
        jdbc.update(
                "insert into assignors "
                        + "(id,legal_name,normalized_tax_id,active,created_at,created_by) "
                        + "values (?,?,?,?,?,?)",
                assignorId,
                "Idempotency Invariant Co " + assignorId,
                "IDEMP-" + assignorId.toString().substring(0, 8),
                true,
                createdAt,
                "migration-test");
        jdbc.update(
                "insert into settlements "
                        + "(id,assignor_id,settlement_currency_code,total_amount,status,"
                        + "created_at,created_by) values (?,?,?,?,?,?,?)",
                settlementId,
                assignorId,
                "BRL",
                new BigDecimal("100.0000"),
                "COMPLETED",
                createdAt,
                "migration-test");
        return settlementId;
    }

    private UUID insertProcessingIdempotencyRecord(String suffix) {
        UUID recordId = UUID.randomUUID();
        jdbc.update(
                "insert into idempotency_records "
                        + "(id,actor,operation,idempotency_key,request_hash,status,created_at) "
                        + "values (?,?,?,?,?,?,?)",
                recordId,
                "idempotency-" + suffix + "-" + recordId + "@srm.local",
                "SETTLEMENT_CREATE",
                "migration-test-" + suffix + "-" + recordId,
                "a".repeat(64),
                "PROCESSING",
                Timestamp.from(Instant.parse("2045-01-01T00:00:00Z")));
        return recordId;
    }

    private static void insertExchangeRate(
            Connection connection, UUID id, String source, Instant observedAt)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into exchange_rates "
                        + "(id,base_currency_code,quote_currency_code,rate,source,observed_at,"
                        + "created_at,created_by) values (?,?,?,?,?,?,?,?)")) {
            statement.setObject(1, id);
            statement.setString(2, "BRL");
            statement.setString(3, "USD");
            statement.setBigDecimal(4, new BigDecimal("0.2000000000"));
            statement.setString(5, source);
            statement.setTimestamp(6, Timestamp.from(observedAt));
            statement.setTimestamp(7, Timestamp.from(observedAt.plusSeconds(1)));
            statement.setString(8, "migration-test");
            statement.executeUpdate();
        }
    }

    private static int countExchangeRatesInHalfOpenWindow(
            Connection connection, String source, Instant from, Instant to)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from exchange_rates "
                        + "where source=? and observed_at>=? and observed_at<?")) {
            statement.setString(1, source);
            statement.setTimestamp(2, Timestamp.from(from));
            statement.setTimestamp(3, Timestamp.from(to));
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static Instant queryInstant(Connection connection, String sql, Object... args)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getTimestamp(1).toInstant();
            }
        }
    }

    private static String queryString(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }
}
