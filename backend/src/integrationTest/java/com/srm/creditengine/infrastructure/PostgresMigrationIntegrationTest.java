package com.srm.creditengine.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
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
                        "insert into base_rate_versions (id,currency_code,monthly_rate,effective_at) values (?,?,?,?)",
                        UUID.randomUUID(),
                        "BRL",
                        new BigDecimal("1.0000000001"),
                        Timestamp.from(effectiveAt)))
                .hasMessageContaining("base_rate_versions_monthly_rate_domain");
        assertThatThrownBy(() -> jdbc.update(
                        "insert into product_spread_versions (id,product_type_code,monthly_spread,effective_at) values (?,?,?,?)",
                        UUID.randomUUID(),
                        "MERCANTILE_INVOICE",
                        new BigDecimal("999"),
                        Timestamp.from(effectiveAt)))
                .hasMessageContaining("product_spread_versions_monthly_spread_domain");
    }
}
