package com.srm.creditengine.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.receivable.application.ReceivableService;
import com.srm.creditengine.settlement.application.SettlementService;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
class SettlementReversalImmutabilityIntegrationTest {
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

    @Test
    void reversalHistoryRejectsUpdateAndDeleteAndPreservesTheOriginalRow() {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(
                assignorId,
                "Reversal Immutability Co",
                "RIM" + assignorId.toString().substring(0, 8),
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
        var quote = pricing.createQuote(receivableId, "BRL", "operator@srm.local");
        var settlement = settlements.settle(
                List.of(quote.id()), "reversal-history-settle-" + receivableId, "operator@srm.local");
        var reversal = settlements.reverse(
                settlement.settlementId(),
                "duplicate source document",
                "reversal-history-reverse-" + receivableId,
                "operator@srm.local");

        ReversalRow original = reversalRow(reversal.reversalId());

        assertThatThrownBy(() -> jdbc.update(
                        "update settlement_reversals set reason = ? where id = ?",
                        "tampered reason",
                        reversal.reversalId()))
                .hasMessageContaining("settlement_reversals rows are immutable");
        assertThatThrownBy(() -> jdbc.update(
                        "delete from settlement_reversals where id = ?", reversal.reversalId()))
                .hasMessageContaining("settlement_reversals rows are immutable");

        assertThat(reversalRow(reversal.reversalId())).isEqualTo(original);
        assertThat(jdbc.queryForObject(
                        "select count(*) from settlement_reversals where id = ?",
                        Integer.class,
                        reversal.reversalId()))
                .isEqualTo(1);
    }

    private ReversalRow reversalRow(UUID reversalId) {
        return jdbc.queryForObject(
                "select id, settlement_id, reason, reversed_at, reversed_by from settlement_reversals where id = ?",
                (resultSet, rowNumber) -> new ReversalRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("settlement_id", UUID.class),
                        resultSet.getString("reason"),
                        resultSet.getTimestamp("reversed_at"),
                        resultSet.getString("reversed_by")),
                reversalId);
    }

    private record ReversalRow(
            UUID id, UUID settlementId, String reason, Timestamp reversedAt, String reversedBy) {}
}
