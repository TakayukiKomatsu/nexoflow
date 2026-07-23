package com.srm.creditengine.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
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
}
