package com.srm.creditengine.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.pricing.ChequePricingStrategy;
import com.srm.creditengine.pricing.InvoicePricingStrategy;
import com.srm.creditengine.pricing.PricingStrategyRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingStrategyRegistryTest {
    private static final Instant AT = Instant.parse("2030-01-15T12:00:00Z");
    private final PricingStrategyRegistry registry =
            new PricingStrategyRegistry(List.of(new InvoicePricingStrategy(), new ChequePricingStrategy()));

    @Test
    void selectsInvoiceAndChequeWithoutAnOrchestrationProductSwitch() {
        assertThat(registry.forProduct("MERCANTILE_INVOICE").code()).isEqualTo("INVOICE_V1");
        assertThat(registry.forProduct("POST_DATED_CHEQUE").code()).isEqualTo("CHEQUE_V1");
    }

    @Test
    void invoiceOwnsItsReferenceSpreadLookupAndDiscount() {
        ReferenceRateService references = mock(ReferenceRateService.class);
        var spread = new ReferenceRateService.ProductSpread(
                "MERCANTILE_INVOICE", new BigDecimal("0.015"), AT);
        when(references.productSpreads("MERCANTILE_INVOICE", AT)).thenReturn(List.of(spread));
        var strategy = registry.forProduct("MERCANTILE_INVOICE");

        var selected = strategy.riskSpread(references, AT);

        assertThat(selected).isSameAs(spread);
        assertThat(strategy.discount(
                                new BigDecimal("1000.00"),
                                new BigDecimal("0.010"),
                                selected.monthlySpread(),
                                BigDecimal.ONE)
                        .setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("975.61");
        verify(references).productSpreads("MERCANTILE_INVOICE", AT);
        verifyNoMoreInteractions(references);
    }

    @Test
    void chequeOwnsItsReferenceSpreadLookupAndDiscount() {
        ReferenceRateService references = mock(ReferenceRateService.class);
        var spread = new ReferenceRateService.ProductSpread(
                "POST_DATED_CHEQUE", new BigDecimal("0.020"), AT);
        when(references.productSpreads("POST_DATED_CHEQUE", AT)).thenReturn(List.of(spread));
        var strategy = registry.forProduct("POST_DATED_CHEQUE");

        var selected = strategy.riskSpread(references, AT);

        assertThat(selected).isSameAs(spread);
        assertThat(strategy.discount(
                                new BigDecimal("1000.00"),
                                new BigDecimal("0.010"),
                                selected.monthlySpread(),
                                BigDecimal.ONE)
                        .setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("970.87");
        verify(references).productSpreads("POST_DATED_CHEQUE", AT);
        verifyNoMoreInteractions(references);
    }

    @Test
    void invoiceReportsMissingEffectiveSpreadSafely() {
        ReferenceRateService references = mock(ReferenceRateService.class);
        when(references.productSpreads("MERCANTILE_INVOICE", AT)).thenReturn(List.of());

        assertThatThrownBy(() -> registry.forProduct("MERCANTILE_INVOICE").riskSpread(references, AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No effective invoice risk spread");
    }

    @Test
    void chequeReportsMissingEffectiveSpreadSafely() {
        ReferenceRateService references = mock(ReferenceRateService.class);
        when(references.productSpreads("POST_DATED_CHEQUE", AT)).thenReturn(List.of());

        assertThatThrownBy(() -> registry.forProduct("POST_DATED_CHEQUE").riskSpread(references, AT))
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
