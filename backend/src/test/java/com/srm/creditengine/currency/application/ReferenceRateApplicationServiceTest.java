package com.srm.creditengine.currency.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReferenceRateApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-15T12:00:00Z");

    @Test
    void rejectsInvalidReferenceVersionsBeforeCallingPorts() {
        ReferenceRateRepository rates = mock(ReferenceRateRepository.class);
        ReferenceRateAuditRecorder audit = mock(ReferenceRateAuditRecorder.class);
        var service = service(rates, audit);

        assertThatThrownBy(() -> service.recordBaseRate("BRL", null, NOW, "admin@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reference rate must be positive");
        assertThatThrownBy(() -> service.recordProductSpread(
                        "MERCANTILE_INVOICE", BigDecimal.ZERO, NOW, "admin@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reference rate must be positive");
        assertThatThrownBy(() -> service.recordBaseRate(
                        "BRL", new BigDecimal("1.0000000001"), NOW, "admin@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reference rate must be at most 1.0000000000");
        assertThatThrownBy(() -> service.recordBaseRate(
                        "BRL", new BigDecimal("0.12345678901"), NOW, "admin@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reference rate must have at most 10 fractional digits");
        assertThatThrownBy(() -> service.recordBaseRate(
                        "BRL", BigDecimal.ONE, null, "admin@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reference rate effective time is required");
        assertThatThrownBy(() -> service.recordBaseRate("BRL", BigDecimal.ONE, NOW, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Actor is required");
        assertThatThrownBy(() -> service.recordBaseRate("BRL", BigDecimal.ONE, NOW, "x".repeat(321)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Actor must not exceed 320 characters");
        assertThatThrownBy(() -> service.recordProductSpread(
                        "UNKNOWN_PRODUCT", BigDecimal.ONE, NOW, "admin@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported product type");
        assertThatThrownBy(() -> service.productSpreads("UNKNOWN_PRODUCT", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported product type");

        verifyNoInteractions(rates, audit);
    }

    @Test
    void recordsAndAuditsBaseRateWithOneStableIdentity() {
        ReferenceRateRepository rates = mock(ReferenceRateRepository.class);
        ReferenceRateAuditRecorder audit = mock(ReferenceRateAuditRecorder.class);
        var service = service(rates, audit);
        var id = ArgumentCaptor.forClass(UUID.class);

        service.recordBaseRate(" brl ", new BigDecimal("0.023"), NOW, "admin@srm.local");

        verify(rates).recordBaseRate(
                id.capture(),
                org.mockito.ArgumentMatchers.eq("BRL"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("0.023")),
                org.mockito.ArgumentMatchers.eq(NOW),
                org.mockito.ArgumentMatchers.eq("admin@srm.local"));
        verify(audit).record(
                "admin@srm.local", "BASE_RATE_RECORDED", "BASE_RATE_VERSION", id.getValue(), NOW);
    }

    @Test
    void recordsAndAuditsProductSpreadAndDelegatesReads() {
        ReferenceRateRepository rates = mock(ReferenceRateRepository.class);
        ReferenceRateAuditRecorder audit = mock(ReferenceRateAuditRecorder.class);
        var service = service(rates, audit);
        var spreads = List.of(new ReferenceRateService.ProductSpread(
                "POST_DATED_CHEQUE", new BigDecimal("0.025"), NOW, "admin@srm.local"));
        var baseRates = List.of(new ReferenceRateService.BaseRate(
                "BRL", new BigDecimal("0.010"), NOW, "admin@srm.local"));
        when(rates.productSpreads("POST_DATED_CHEQUE", NOW)).thenReturn(spreads);
        when(rates.baseRates("BRL", NOW)).thenReturn(baseRates);

        service.recordProductSpread(
                "POST_DATED_CHEQUE", new BigDecimal("0.025"), NOW, "admin@srm.local");

        var id = ArgumentCaptor.forClass(UUID.class);
        verify(rates).recordProductSpread(
                id.capture(),
                org.mockito.ArgumentMatchers.eq("POST_DATED_CHEQUE"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("0.025")),
                org.mockito.ArgumentMatchers.eq(NOW),
                org.mockito.ArgumentMatchers.eq("admin@srm.local"));
        verify(audit).record(
                "admin@srm.local", "PRODUCT_SPREAD_RECORDED", "PRODUCT_SPREAD_VERSION", id.getValue(), NOW);
        assertThat(service.productSpreads("POST_DATED_CHEQUE", NOW)).isSameAs(spreads);
        assertThat(service.baseRates("BRL", NOW)).isSameAs(baseRates);
    }

    private static ReferenceRateApplicationService service(
            ReferenceRateRepository rates, ReferenceRateAuditRecorder audit) {
        return new ReferenceRateApplicationService(
                rates, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
