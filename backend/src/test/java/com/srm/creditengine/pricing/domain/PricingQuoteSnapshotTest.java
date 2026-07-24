package com.srm.creditengine.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PricingQuoteSnapshotTest {
    @Test
    void preservesEveryFinancialDecisionFieldInQuoteSnapshot() {
        var snapshot = new PricingQuoteSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), "MERCANTILE_INVOICE", LocalDate.parse("2030-02-14"),
                "USD", new BigDecimal("1000.0000"), "BRL", Instant.parse("2030-01-15T12:00:00Z"),
                Instant.parse("2030-01-15T12:15:00Z"), new BigDecimal("0.0100000000"),
                new BigDecimal("0.0150000000"), "INVOICE", "ACTUAL_DAYS_30_MONTH", new BigDecimal("1.0000000000"),
                new BigDecimal("966.1800"), "BRL", "USD", new BigDecimal("0.2000000000"), "TEST",
                Instant.parse("2030-01-15T12:00:00Z"), new BigDecimal("966.18"), "operator", "ACTIVE");

        assertThat(snapshot.settlementAmount()).isEqualByComparingTo("966.18");
        assertThat(snapshot.strategyCode()).isEqualTo("INVOICE");
        assertThat(snapshot.fxObservedAt()).isEqualTo(Instant.parse("2030-01-15T12:00:00Z"));
    }
}
