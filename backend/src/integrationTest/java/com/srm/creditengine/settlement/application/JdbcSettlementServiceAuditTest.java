package com.srm.creditengine.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.receivable.application.ReceivableService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
class JdbcSettlementServiceAuditTest {

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
    void AUDIT_001_settleAndReverseEachWriteAnAppendOnlyAuditEvent() {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(assignorId, "Audit Co", "AUD" + assignorId.toString().substring(0, 8), true, "operator@srm.local"));
        UUID receivableId = UUID.randomUUID();
        receivables.register(new ReceivableService.RegisterCommand(receivableId, assignorId, "MERCANTILE_INVOICE", new BigDecimal("1000.00"), "BRL", LocalDate.parse("2030-01-01"), LocalDate.parse("2030-02-14"), "operator@srm.local"));
        var quote = pricing.createQuote(receivableId, "BRL", "operator@srm.local");

        var settled = settlements.settle(List.of(quote.id()), "audit-test-key", "operator@srm.local");

        Map<String, Object> settleEvent = jdbc.queryForMap(
                "select actor, action, target_type, target_id from audit_events where target_id=? and action='SETTLEMENT_CREATED'", settled.settlementId());
        assertThat(settleEvent.get("actor")).isEqualTo("operator@srm.local");
        assertThat(settleEvent.get("target_type")).isEqualTo("SETTLEMENT");

        var reversed = settlements.reverse(settled.settlementId(), "duplicate source document", "audit-test-reverse-key", "operator@srm.local");

        Map<String, Object> reverseEvent = jdbc.queryForMap(
                "select actor, action, target_type, target_id from audit_events where target_id=? and action='SETTLEMENT_REVERSED'", reversed.reversalId());
        assertThat(reverseEvent.get("actor")).isEqualTo("operator@srm.local");
        assertThat(reverseEvent.get("target_type")).isEqualTo("SETTLEMENT_REVERSAL");

        int count = jdbc.queryForObject("select count(*) from audit_events where action in ('SETTLEMENT_CREATED','SETTLEMENT_REVERSED')", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(2);
    }
}
