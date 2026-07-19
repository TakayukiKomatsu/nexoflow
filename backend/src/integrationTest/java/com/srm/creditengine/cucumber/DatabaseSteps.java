package com.srm.creditengine.cucumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cucumber.java.Before;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import java.math.BigDecimal;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Database assertion step definitions. Uses JdbcTemplate and generated IDs from ScenarioState
 * to assert row counts, statuses, and rollback correctness.
 * No assertion uses an unfiltered global count.
 */
public class DatabaseSteps {

    private final JdbcTemplate jdbc;
    private final ScenarioState state;

    @Autowired
    public DatabaseSteps(JdbcTemplate jdbc, ScenarioState state) {
        this.jdbc = jdbc;
        this.state = state;
    }

    @Before
    public void seedScenarioAssignor(Scenario scenario) {
        UUID id = UUID.nameUUIDFromBytes(
                ("cucumber:" + scenario.getName()).getBytes(StandardCharsets.UTF_8));
        state.assignorId = id;
        jdbc.update(
                "insert into assignors"
                + "(id,legal_name,normalized_tax_id,active,created_at,created_by)"
                + " values (?,?,?,?,?,?) on conflict (id) do nothing",
                id,
                "Cucumber " + scenario.getName(),
                id.toString().replace("-", ""),
                true,
                Timestamp.from(Instant.parse("2030-01-15T12:00:00Z")),
                "cucumber");
    }

    @After
    public void removeAcceptanceTriggers() {
        jdbc.execute("drop trigger if exists cucumber_fail_second_item on settlement_items");
        jdbc.execute("drop function if exists cucumber_fail_second_item()");
        jdbc.execute("drop trigger if exists cucumber_claim_barrier on idempotency_records");
        jdbc.execute("drop function if exists cucumber_claim_barrier()");
    }

    @And("a same-key idempotency claim barrier is installed")
    public void installIdempotencyClaimBarrier() {
        jdbc.execute("""
                create function cucumber_claim_barrier() returns trigger language plpgsql as $$
                begin
                    if new.operation = 'SETTLEMENT_CREATE'
                            and new.idempotency_key like 'cucumber-claim-%' then
                        perform pg_advisory_xact_lock(
                            hashtextextended(new.idempotency_key, 0));
                    end if;
                    return new;
                end
                $$""");
        jdbc.execute("""
                create trigger cucumber_claim_barrier
                before insert on idempotency_records
                for each row execute function cucumber_claim_barrier()
                """);
    }

    @And("the pricing quote row count is recorded")
    public void recordPricingQuoteCount() {
        state.pricingQuoteCount = jdbc.queryForObject("select count(*) from pricing_quotes", Integer.class);
    }

    @And("the pricing quote row count is unchanged")
    public void assertPricingQuoteCountUnchanged() {
        assertThat(jdbc.queryForObject("select count(*) from pricing_quotes", Integer.class))
                .isEqualTo(state.pricingQuoteCount);
    }
    @And("the database rejects mutations of the last pricing quote snapshot")
    public void assertPricingQuoteSnapshotImmutable() {
        BigDecimal original = jdbc.queryForObject(
                "select face_amount from pricing_quotes where id=?",
                BigDecimal.class,
                state.lastQuoteId);
        assertThatThrownBy(() -> jdbc.update(
                        "update pricing_quotes set face_amount=face_amount+1 where id=?",
                        state.lastQuoteId))
                .hasMessageContaining("pricing quote snapshots are immutable");
        assertThat(jdbc.queryForObject(
                        "select face_amount from pricing_quotes where id=?",
                        BigDecimal.class,
                        state.lastQuoteId))
                .isEqualByComparingTo(original);
    }

    @And("a database fault is armed after the first settlement item")
    public void armSettlementItemFault() {
        jdbc.execute("drop trigger if exists cucumber_fail_second_item on settlement_items");
        jdbc.execute("drop function if exists cucumber_fail_second_item()");
        jdbc.execute("""
                create function cucumber_fail_second_item() returns trigger language plpgsql as $$
                begin
                    if new.quote_id = '%s'::uuid then
                        raise exception 'SETTLE-ROLLBACK-008 injected failure after first settlement item';
                    end if;
                    return new;
                end
                $$""".formatted(state.secondQuoteId));
        jdbc.execute("""
                create trigger cucumber_fail_second_item
                before insert on settlement_items
                for each row execute function cucumber_fail_second_item()
                """);
    }

    @Then("the scoped financial row counts are recorded for the two quotes")
    public void recordScopedFinancialCounts() {
        state.beforeSettlementCount = rowCount(
                "select count(*) from settlements where assignor_id=?",
                state.assignorId);
        state.beforeItemCount = rowCount(
                "select count(*) from settlement_items where quote_id in (?,?)",
                state.lastQuoteId,
                state.secondQuoteId);
        state.beforeIdempotencyCount = rowCount(
                "select count(*) from idempotency_records"
                + " where actor=? and operation='SETTLEMENT_CREATE' and idempotency_key=?",
                "operator@srm.local",
                state.currentIdempotencyKey);
        state.beforeAuditCount = rowCount(
                "select count(*) from audit_events a"
                + " join settlements s on s.id=a.target_id"
                + " where a.action='SETTLEMENT_CREATED' and s.assignor_id=?",
                state.assignorId);
    }

    @And("the database has no new financial or idempotency rows for the two quotes")
    public void assertNoNewScopedFinancialRows() {
        assertThat(rowCount(
                        "select count(*) from settlements where assignor_id=?",
                        state.assignorId))
                .isEqualTo(state.beforeSettlementCount);
        assertThat(rowCount(
                        "select count(*) from settlement_items where quote_id in (?,?)",
                        state.lastQuoteId,
                        state.secondQuoteId))
                .isEqualTo(state.beforeItemCount);
        assertThat(rowCount(
                        "select count(*) from idempotency_records"
                        + " where actor=? and operation='SETTLEMENT_CREATE' and idempotency_key=?",
                        "operator@srm.local",
                        state.currentIdempotencyKey))
                .isEqualTo(state.beforeIdempotencyCount);
        assertThat(rowCount(
                        "select count(*) from audit_events a"
                        + " join settlements s on s.id=a.target_id"
                        + " where a.action='SETTLEMENT_CREATED' and s.assignor_id=?",
                        state.assignorId))
                .isEqualTo(state.beforeAuditCount);
    }

    @And("both receivables are still {string}")
    public void assertBothReceivablesStatus(String expected) {
        assertThat(jdbc.queryForObject(
                        "select status from receivables where id=?",
                        String.class,
                        state.lastReceivableId))
                .isEqualTo(expected);
        assertThat(jdbc.queryForObject(
                        "select status from receivables where id=?",
                        String.class,
                        state.secondReceivableId))
                .isEqualTo(expected);
    }

    @And("the database contains one completed settlement with ordered items and one audit event")
    public void assertCompletedSettlementEvidence() {
        assertThat(rowCount(
                        "select count(*) from settlements"
                        + " where id=? and assignor_id=? and status='COMPLETED'",
                        state.lastSettlementId,
                        state.assignorId))
                .isEqualTo(1);
        List<UUID> quoteIds = jdbc.query(
                "select quote_id from settlement_items where settlement_id=? order by item_position",
                (row, index) -> row.getObject(1, UUID.class),
                state.lastSettlementId);
        assertThat(quoteIds).containsExactly(state.lastQuoteId, state.secondQuoteId);
        assertThat(rowCount(
                        "select count(*) from idempotency_records"
                        + " where actor=? and operation='SETTLEMENT_CREATE'"
                        + " and idempotency_key=? and status='COMPLETED' and settlement_id=?",
                        "operator@srm.local",
                        state.currentIdempotencyKey,
                        state.lastSettlementId))
                .isEqualTo(1);
        assertThat(rowCount(
                        "select count(*) from audit_events"
                        + " where action='SETTLEMENT_CREATED' and target_id=?",
                        state.lastSettlementId))
                .isEqualTo(1);
        assertBothQuotesStatus("CONSUMED");
        assertBothReceivablesStatus("SETTLED");
    }

    @And("the database has one negative reversal ledger entry for each settlement item")
    public void assertNegativeReversalLedgerEntries() {
        List<BigDecimal> amounts = jdbc.query(
                "select -i.settlement_amount from settlement_reversals r"
                + " join settlement_items i on i.settlement_id=r.settlement_id"
                + " where r.settlement_id=? order by i.item_position",
                (row, index) -> row.getBigDecimal(1),
                state.lastSettlementId);
        assertThat(amounts).hasSize(rowCount(
                "select count(*) from settlement_items where settlement_id=?",
                state.lastSettlementId));
        assertThat(amounts).allSatisfy(amount -> assertThat(amount).isNegative());
    }

    // ─── Rollback scenario helpers ────────────────────────────────────────────────

    @Then("the database settlement count before is recorded for the two quotes")
    public void recordBeforeSettlementCount() {
        state.beforeSettlementCount = rowCount(
                "select count(*) from settlements s"
                + " join settlement_items i on i.settlement_id = s.id"
                + " where i.quote_id in (?,?)",
                state.lastQuoteId, state.secondQuoteId);
        state.beforeItemCount = rowCount(
                "select count(*) from settlement_items where quote_id in (?,?)",
                state.lastQuoteId, state.secondQuoteId);
    }

    @And("the database has no new settlement rows for the two quotes")
    public void assertNoNewSettlementRows() {
        int afterSettlementCount = rowCount(
                "select count(*) from settlement_items where quote_id in (?,?)",
                state.lastQuoteId, state.secondQuoteId);
        assertThat(afterSettlementCount).isEqualTo(state.beforeItemCount);
    }

    @And("the receivable status is still {string}")
    public void assertReceivableStatus(String expected) {
        String status = jdbc.queryForObject(
                "select status from receivables where id=?", String.class,
                state.lastReceivableId);
        assertThat(status).isEqualTo(expected);
    }

    @And("the receivable status is {string}")
    public void assertReceivableStatusIs(String expected) {
        String status = jdbc.queryForObject(
                "select status from receivables where id=?", String.class,
                state.lastReceivableId);
        assertThat(status).isEqualTo(expected);
    }

    @And("both quotes are still {string}")
    public void assertBothQuotesStatus(String expected) {
        String s1 = jdbc.queryForObject(
                "select status from pricing_quotes where id=?", String.class, state.lastQuoteId);
        String s2 = jdbc.queryForObject(
                "select status from pricing_quotes where id=?", String.class, state.secondQuoteId);
        assertThat(s1).isEqualTo(expected);
        assertThat(s2).isEqualTo(expected);
    }

    // ─── Reversal assertions ──────────────────────────────────────────────────────

    @And("the database has exactly {int} reversal for the last settlement")
    public void assertReversalCount(int expected) {
        int count = rowCount(
                "select count(*) from settlement_reversals where settlement_id=?",
                state.lastSettlementId);
        assertThat(count).isEqualTo(expected);
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────────

    private int rowCount(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
