package com.srm.creditengine.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.receivable.application.ReceivableService;
import com.srm.creditengine.reporting.application.SettlementStatementService;
import com.srm.creditengine.settlement.domain.AlreadyReversedException;
import com.srm.creditengine.settlement.application.IdempotencyKeyReusedException;
import com.srm.creditengine.settlement.application.SettlementService;
import java.math.BigDecimal;
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

@Testcontainers
@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00.123456789Z")
class SettlementReversalIntegrationTest {
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

    @Autowired SettlementService settlements;
    @Autowired PricingService pricing;
    @Autowired AssignorService assignors;
    @Autowired ReceivableService receivables;
    @Autowired JdbcTemplate jdbc;
    @Autowired SettlementStatementService statements;

    @Test
    void wholeReversalIsIdempotentTerminalAtomicAndProducesOneNegativeMovementPerItem() {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(
                assignorId,
                "Whole Reversal Co",
                "WREV" + assignorId.toString().substring(0, 8),
                true,
                "operator@srm.local"));
        List<UUID> receivableIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> quoteIds = receivableIds.stream()
                .map(receivableId -> createQuote(assignorId, receivableId))
                .toList();
        var settlement = settlements.settle(
                quoteIds, "whole-reversal-settle-" + assignorId, "operator@srm.local");
        String reason = "duplicate source document";

        String failedKey = "whole-reversal-failed-" + assignorId;
        String suffix = assignorId.toString().replace("-", "");
        String function = "fail_reversal_" + suffix;
        String trigger = "fail_reversal_trigger_" + suffix;
        jdbc.execute("""
                create function %s() returns trigger language plpgsql as $$
                begin
                    if new.id = '%s'::uuid and new.status = 'REVERSED' then
                        raise exception 'injected mid-reversal failure';
                    end if;
                    return new;
                end;
                $$
                """.formatted(function, receivableIds.get(1)));
        jdbc.execute("create trigger " + trigger
                + " before update on receivables for each row execute function " + function + "()");
        try {
            assertThatThrownBy(() -> settlements.reverse(
                            settlement.settlementId(), reason, failedKey, "operator@srm.local"))
                    .hasMessageContaining("injected mid-reversal failure");
        } finally {
            jdbc.execute("drop trigger " + trigger + " on receivables");
            jdbc.execute("drop function " + function + "()");
        }

        assertThat(receivableStatuses(receivableIds))
                .containsExactly("SETTLED", "SETTLED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from settlement_reversals where settlement_id=?",
                        Integer.class,
                        settlement.settlementId()))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_events "
                                + "where action='SETTLEMENT_REVERSED' "
                                + "and safe_metadata->>'settlementId'=?",
                        Integer.class,
                        settlement.settlementId().toString()))
                .isZero();
        assertThat(idempotencyCount(failedKey)).isZero();

        String reversalKey = "whole-reversal-reverse-" + assignorId;
        var first = settlements.reverse(
                settlement.settlementId(), reason, reversalKey, "operator@srm.local");
        var replay = settlements.reverse(
                settlement.settlementId(), reason, reversalKey, "operator@srm.local");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.reversalId()).isEqualTo(first.reversalId());
        assertThat(replay.settlementId()).isEqualTo(first.settlementId());
        assertThat(replay.reason()).isEqualTo(first.reason());
        assertThat(replay.reversedAt()).isEqualTo(first.reversedAt());
        assertThat(receivableStatuses(receivableIds))
                .containsExactly("REVERSED", "REVERSED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from receivables where id in (?, ?) and status='REGISTERED'",
                        Integer.class,
                        receivableIds.get(0),
                        receivableIds.get(1)))
                .isZero();

        var ledger = statements.query(new SettlementStatementService.Filter(
                null, null, assignorId, null, null, null, 0, 100));
        var reversalEntries = ledger.entries().stream()
                .filter(entry -> "REVERSAL".equals(entry.entryType()))
                .toList();
        assertThat(reversalEntries)
                .hasSize(receivableIds.size())
                .allSatisfy(entry -> {
                    assertThat(entry.reversalId()).isEqualTo(first.reversalId());
                    assertThat(entry.settlementId()).isEqualTo(settlement.settlementId());
                    assertThat(entry.signedAmount()).isNegative();
                });
        assertThat(reversalEntries.stream()
                        .map(SettlementStatementService.Entry::receivableId)
                        .toList())
                .containsExactlyInAnyOrderElementsOf(receivableIds);

        assertThatThrownBy(() -> settlements.reverse(
                        settlement.settlementId(),
                        "different reason",
                        reversalKey,
                        "operator@srm.local"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        String differentKey = "different-reversal-key-" + assignorId;
        assertThatThrownBy(() -> settlements.reverse(
                        settlement.settlementId(),
                        reason,
                        differentKey,
                        "operator@srm.local"))
                .isInstanceOf(AlreadyReversedException.class);

        assertThat(jdbc.queryForObject(
                        "select count(*) from settlement_reversals where settlement_id=?",
                        Integer.class,
                        settlement.settlementId()))
                .isEqualTo(1);
        assertThat(idempotencyCount(reversalKey)).isEqualTo(1);
        assertThat(idempotencyCount(differentKey)).isZero();
    }

    private List<String> receivableStatuses(List<UUID> receivableIds) {
        return jdbc.queryForList(
                "select status from receivables where id in (?, ?) order by id",
                String.class,
                receivableIds.get(0),
                receivableIds.get(1));
    }

    private int idempotencyCount(String key) {
        return jdbc.queryForObject(
                "select count(*) from idempotency_records "
                        + "where actor='operator@srm.local' "
                        + "and operation='SETTLEMENT_REVERSE' and idempotency_key=?",
                Integer.class,
                key);
    }

    private UUID createQuote(UUID assignorId, UUID receivableId) {
        receivables.register(new ReceivableService.RegisterCommand(
                receivableId,
                assignorId,
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.00"),
                "BRL",
                LocalDate.parse("2030-01-01"),
                LocalDate.parse("2030-02-14"),
                "operator@srm.local"));
        return pricing.createQuote(receivableId, "BRL", "operator@srm.local").id();
    }
}
