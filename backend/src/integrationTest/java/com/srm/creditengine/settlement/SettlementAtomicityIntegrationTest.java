package com.srm.creditengine.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.settlement.application.SettlementService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-Postgres proof that a mid-transaction settlement failure rolls back every
 * financial mutation. The test drives the real Spring-managed service against a
 * Testcontainers Postgres instance and scopes all evidence to generated fixture IDs.
 *
 * Scenario SETTLE-ROLLBACK-008 is traced from
 * docs/sdd/05_sdd_settlement-preview-and-atomic-idempotent-settlement.md:
 * a mid-transaction failure must leave nothing persisted.
 */
@Testcontainers
@SpringBootTest
class SettlementAtomicityIntegrationTest {
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

    @Autowired private SettlementService settlements;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void previewRejectsDistinctQuotesForTheSameReceivable() {
        UUID assignorId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        UUID quoteOne = UUID.randomUUID();
        UUID quoteTwo = UUID.randomUUID();
        insertAssignor(assignorId);
        insertReceivable(receivableId, assignorId, "REGISTERED", 0);
        insertPricingQuote(quoteOne, receivableId);
        insertPricingQuote(quoteTwo, receivableId);

        assertThatThrownBy(() -> settlements.preview(
                        List.of(quoteOne, quoteTwo), "preview-operator@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pricing quotes must reference unique receivables");
    }

    // --- SETTLE-ROLLBACK-008: a mid-transaction failure must leave nothing persisted ---
    @Test
    void SETTLE_ROLLBACK_008_multiItemSettlementRollsBackAllScopedStateOnMidTransactionFailure() {
        UUID assignorId = UUID.randomUUID();
        UUID receivableOne = UUID.randomUUID();
        UUID receivableTwo = UUID.randomUUID();
        UUID quoteOne = UUID.randomUUID();
        UUID quoteTwo = UUID.randomUUID();
        String idempotencyKey = "rollback-key-" + UUID.randomUUID();
        String actor = "actor-rollback-" + UUID.randomUUID() + "@srm.local";
        insertAssignor(assignorId);
        insertReceivable(receivableOne, assignorId, "REGISTERED", 0);
        insertReceivable(receivableTwo, assignorId, "REGISTERED", 0);
        insertPricingQuote(quoteOne, receivableOne);
        insertPricingQuote(quoteTwo, receivableTwo);
        String failureTrigger = installReceivableFailureTrigger(receivableTwo);

        int settlementsBefore = rowCount(
                "select count(*) from settlements where assignor_id=? and created_by=?", assignorId, actor);
        int itemsBefore = rowCount(
                "select count(*) from settlement_items where quote_id in (?,?)", quoteOne, quoteTwo);
        int idempotencyBefore = rowCount(
                "select count(*) from idempotency_records where actor=? and operation='SETTLEMENT_CREATE' and idempotency_key=?",
                actor, idempotencyKey);
        int auditEventsBefore = rowCount(
                "select count(*) from audit_events where actor=? and action='SETTLEMENT_CREATED'", actor);

        try {
            assertThatThrownBy(() -> settlements.settle(List.of(quoteOne, quoteTwo), idempotencyKey, actor))
                    .hasMessageContaining("forced settlement failure");
        } finally {
            removeReceivableFailureTrigger(failureTrigger);
        }

        assertThat(rowCount("select count(*) from settlements where assignor_id=? and created_by=?", assignorId, actor))
                .isEqualTo(settlementsBefore);
        assertThat(rowCount("select count(*) from settlement_items where quote_id in (?,?)", quoteOne, quoteTwo))
                .isEqualTo(itemsBefore);
        assertThat(rowCount(
                        "select count(*) from idempotency_records where actor=? and operation='SETTLEMENT_CREATE' and idempotency_key=?",
                        actor, idempotencyKey))
                .isEqualTo(idempotencyBefore);

        assertThat(rowCount(
                        "select count(*) from receivables where id in (?,?) and status='REGISTERED' and version=0",
                        receivableOne,
                        receivableTwo))
                .isEqualTo(2);

        String quoteOneStatus = jdbc.queryForObject("select status from pricing_quotes where id=?", String.class, quoteOne);
        String quoteTwoStatus = jdbc.queryForObject("select status from pricing_quotes where id=?", String.class, quoteTwo);
        assertThat(quoteOneStatus).isEqualTo("ACTIVE");
        assertThat(quoteTwoStatus).isEqualTo("ACTIVE");

        assertThat(rowCount("select count(*) from audit_events where actor=? and action='SETTLEMENT_CREATED'", actor))
                .isEqualTo(auditEventsBefore);
    }

    private String installReceivableFailureTrigger(UUID receivableId) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String functionName = "fail_settlement_" + suffix;
        String triggerName = "fail_settlement_trigger_" + suffix;
        jdbc.execute("""
                create function %s() returns trigger language plpgsql as $$
                begin
                    if new.id = '%s'::uuid then
                        raise exception 'forced settlement failure';
                    end if;
                    return new;
                end
                $$
                """.formatted(functionName, receivableId));
        jdbc.execute("""
                create trigger %s before update of status on receivables
                for each row execute function %s()
                """.formatted(triggerName, functionName));
        return triggerName + ":" + functionName;
    }

    private void removeReceivableFailureTrigger(String failureTrigger) {
        String[] names = failureTrigger.split(":");
        jdbc.execute("drop trigger if exists " + names[0] + " on receivables");
        jdbc.execute("drop function if exists " + names[1] + "()");
    }

    private int rowCount(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private void insertAssignor(UUID id) {
        jdbc.update("insert into assignors (id,legal_name,normalized_tax_id,active,created_at,created_by) values (?,?,?,?,?,?)",
                id, "Atomicity Test Assignor " + id, "TAX-" + id.toString().substring(0, 8), true, Timestamp.from(Instant.now()), "integration-test");
    }

    private void insertReceivable(UUID id, UUID assignorId, String status, int version) {
        jdbc.update("insert into receivables (id,assignor_id,product_type_code,face_currency_code,face_amount,issue_date,due_date,status,version,created_at,created_by) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?)",
                id, assignorId, "MERCANTILE_INVOICE", "BRL", new BigDecimal("2000.0000"),
                LocalDate.now().minusDays(30), LocalDate.now().plusDays(30), status, version, Timestamp.from(Instant.now()), "integration-test");
    }

    private void insertPricingQuote(UUID id, UUID receivableId) {
        Instant now = Instant.now();
        jdbc.update("insert into pricing_quotes (id,receivable_id,settlement_currency_code,face_amount,face_currency_code,product_type_code,due_date,pricing_at,expires_at,"
                        + "base_rate,spread,strategy_code,day_count_convention,term_in_months,discounted_amount,fx_base_currency_code,fx_quote_currency_code,"
                        + "fx_rate,fx_source,fx_observed_at,settlement_amount,created_by,status) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, receivableId, "BRL", new BigDecimal("2000.0000"), "BRL", "MERCANTILE_INVOICE", LocalDate.now().plusDays(30),
                Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3600)),
                new BigDecimal("0.0100000000"), new BigDecimal("0.0050000000"), "MERCANTILE_INVOICE", "ACTUAL_360",
                new BigDecimal("1.0000000000"), new BigDecimal("1900.0000"), "BRL", "BRL",
                new BigDecimal("1.0000000000"), "integration-test-fixture", Timestamp.from(now.minusSeconds(60)),
                new BigDecimal("1900.0000"), "integration-test", "ACTIVE");
    }
}
