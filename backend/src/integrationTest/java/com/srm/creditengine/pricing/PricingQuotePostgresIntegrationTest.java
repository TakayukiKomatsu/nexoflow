package com.srm.creditengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.receivable.application.ReceivableService;
import java.sql.DriverManager;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
class PricingQuotePostgresIntegrationTest {
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

    @Autowired AssignorService assignors;
    @Autowired ReceivableService receivables;
    @Autowired PricingService pricing;
    @Autowired JdbcTemplate jdbc;

    @Test
    void quoteRejectsEverySnapshotMutationAndDeleteButAllowsConsumption() {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(
                assignorId,
                "Postgres Snapshot Ltd",
                "PGSNAP" + assignorId.toString().substring(0, 8),
                true,
                "operator@srm.local"));
        UUID receivableId = UUID.randomUUID();
        receivables.register(new ReceivableService.RegisterCommand(
                receivableId,
                assignorId,
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.00"),
                "BRL",
                LocalDate.parse("2030-01-01"),
                LocalDate.parse("2030-02-14"),
                "operator@srm.local"));
        UUID quoteId = pricing.createQuote(receivableId, "BRL", "operator@srm.local").id();

        List<SnapshotMutation> mutations = List.of(
                new SnapshotMutation("id", UUID.randomUUID()),
                new SnapshotMutation("receivable_id", UUID.randomUUID()),
                new SnapshotMutation("settlement_currency_code", "USD"),
                new SnapshotMutation("face_amount", new BigDecimal("1001.0000")),
                new SnapshotMutation("face_currency_code", "USD"),
                new SnapshotMutation("product_type_code", "POST_DATED_CHEQUE"),
                new SnapshotMutation("due_date", Date.valueOf("2030-02-15")),
                new SnapshotMutation("pricing_at", Timestamp.from(Instant.parse("2030-01-15T12:00:01Z"))),
                new SnapshotMutation("expires_at", Timestamp.from(Instant.parse("2030-01-15T12:16:00Z"))),
                new SnapshotMutation("base_rate", new BigDecimal("0.0200000000")),
                new SnapshotMutation("spread", new BigDecimal("0.0250000000")),
                new SnapshotMutation("strategy_code", "OTHER_STRATEGY"),
                new SnapshotMutation("day_count_convention", "OTHER_CONVENTION"),
                new SnapshotMutation("term_in_months", new BigDecimal("2.0000000000")),
                new SnapshotMutation("discounted_amount", new BigDecimal("900.0000")),
                new SnapshotMutation("fx_base_currency_code", "USD"),
                new SnapshotMutation("fx_quote_currency_code", "USD"),
                new SnapshotMutation("fx_rate", new BigDecimal("2.0000000000")),
                new SnapshotMutation("fx_source", "OTHER_SOURCE"),
                new SnapshotMutation("fx_observed_at", Timestamp.from(Instant.parse("2030-01-15T12:00:01Z"))),
                new SnapshotMutation("settlement_amount", new BigDecimal("900.0000")),
                new SnapshotMutation("created_by", "other@srm.local"));

        for (SnapshotMutation mutation : mutations) {
            assertThatThrownBy(() -> jdbc.update(
                            "update pricing_quotes set " + mutation.column() + " = ? where id = ?",
                            mutation.value(),
                            quoteId))
                    .as("snapshot column %s is immutable", mutation.column())
                    .hasMessageContaining("pricing quote snapshots are immutable");
        }

        assertThatThrownBy(() -> jdbc.update(
                        """
                        update pricing_quotes
                        set status = 'CONSUMED', product_type_code = 'POST_DATED_CHEQUE'
                        where id = ? and status = 'ACTIVE'
                        """,
                        quoteId))
                .as("consumption cannot be combined with a snapshot mutation")
                .hasMessageContaining("pricing quote snapshots are immutable");

        assertThat(jdbc.update(
                        "update pricing_quotes set status = 'CONSUMED' where id = ? and status = 'ACTIVE'",
                        quoteId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select status from pricing_quotes where id = ?",
                        String.class,
                        quoteId))
                .isEqualTo("CONSUMED");
        assertThatThrownBy(() -> jdbc.update("delete from pricing_quotes where id = ?", quoteId))
                .hasMessageContaining("pricing quotes are immutable");
    }

    @Test
    void migrationBackfillsLegacyQuotesBeforeRestoringTheImmutabilityTrigger() throws Exception {
        String schema = "quote_backfill_" + UUID.randomUUID().toString().replace("-", "");
        UUID assignorId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("14"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.execute("""
                    insert into assignors
                        (id, legal_name, normalized_tax_id, active, created_at, created_by)
                    values ('%s', 'Legacy Snapshot Ltd', 'LEGACY%s', true,
                            timestamp '2030-01-15 12:00:00', 'migration-test')
                    """.formatted(assignorId, assignorId.toString().substring(0, 8)));
            statement.execute("""
                    insert into receivables
                        (id, assignor_id, product_type_code, face_currency_code, face_amount,
                         issue_date, due_date, status, version, created_at, created_by)
                    values ('%s', '%s', 'MERCANTILE_INVOICE', 'BRL', 1000.0000,
                            date '2030-01-01', date '2030-02-14', 'REGISTERED', 0,
                            timestamp '2030-01-15 12:00:00', 'migration-test')
                    """.formatted(receivableId, assignorId));
            statement.execute("""
                    insert into pricing_quotes
                        (id, receivable_id, settlement_currency_code, face_amount,
                         face_currency_code, due_date, pricing_at, expires_at, base_rate,
                         spread, strategy_code, day_count_convention, term_in_months,
                         discounted_amount, fx_base_currency_code, fx_quote_currency_code,
                         fx_rate, fx_source, fx_observed_at, settlement_amount, created_by)
                    values ('%s', '%s', 'BRL', 1000.0000, 'BRL', date '2030-02-14',
                            timestamp '2030-01-15 12:00:00', timestamp '2030-01-15 12:15:00',
                            0.0100000000, 0.0150000000, 'INVOICE_V1',
                            'ACTUAL_DAYS_30_MONTH', 1.0000000000, 975.6098,
                            'BRL', 'BRL', 1.0000000000, 'IDENTITY',
                            timestamp '2030-01-15 12:00:00', 975.6100, 'migration-test')
                    """.formatted(quoteId, receivableId));
        }

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            connection.setSchema(schema);
            try (var rows = statement.executeQuery(
                    "select product_type_code from pricing_quotes where id = '" + quoteId + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("product_type_code")).isEqualTo("MERCANTILE_INVOICE");
            }
            assertThatThrownBy(() -> statement.executeUpdate(
                            "update pricing_quotes set product_type_code = 'POST_DATED_CHEQUE' "
                                    + "where id = '" + quoteId + "'"))
                    .hasMessageContaining("pricing quote snapshots are immutable");
        }
    }

    private record SnapshotMutation(String column, Object value) {}
}
