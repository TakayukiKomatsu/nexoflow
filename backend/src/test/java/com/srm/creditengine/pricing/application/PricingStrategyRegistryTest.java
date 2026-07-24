package com.srm.creditengine.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.pricing.domain.ChequePricingStrategy;
import com.srm.creditengine.pricing.domain.InvoicePricingStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingStrategyRegistryTest {
    private final PricingStrategyRegistry registry =
            new PricingStrategyRegistry(List.of(new InvoicePricingStrategy(), new ChequePricingStrategy()));

    @Test
    void selectsInvoiceAndChequeWithoutAnOrchestrationProductSwitch() {
        assertThat(registry.forProduct("MERCANTILE_INVOICE").code()).isEqualTo("INVOICE_V1");
        assertThat(registry.forProduct("POST_DATED_CHEQUE").code()).isEqualTo("CHEQUE_V1");
    }

    @Test
    void invoiceOwnsItsSpreadSelectionAndDiscount() {
        var strategy = registry.forProduct("MERCANTILE_INVOICE");

        var selected = strategy.riskSpread(List.of(new BigDecimal("0.015")));

        assertThat(selected).isEqualByComparingTo("0.015");
        assertThat(strategy.discount(
                                new BigDecimal("1000.00"),
                                new BigDecimal("0.010"),
                                selected,
                                BigDecimal.ONE)
                        .setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("975.61");
    }

    @Test
    void chequeOwnsItsSpreadSelectionAndDiscount() {
        var strategy = registry.forProduct("POST_DATED_CHEQUE");

        var selected = strategy.riskSpread(List.of(new BigDecimal("0.020")));

        assertThat(selected).isEqualByComparingTo("0.020");
        assertThat(strategy.discount(
                                new BigDecimal("1000.00"),
                                new BigDecimal("0.010"),
                                selected,
                                BigDecimal.ONE)
                        .setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("970.87");
    }

    @Test
    void invoiceReportsMissingEffectiveSpreadSafely() {
        assertThatThrownBy(() -> registry.forProduct("MERCANTILE_INVOICE").riskSpread(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No effective invoice risk spread");
    }

    @Test
    void chequeReportsMissingEffectiveSpreadSafely() {
        assertThatThrownBy(() -> registry.forProduct("POST_DATED_CHEQUE").riskSpread(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No effective cheque risk spread");
    }

    @Test
    void rejectsUnknownProduct() {
        assertThatThrownBy(() -> registry.forProduct("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported product type: UNKNOWN");
    }
}
