package com.srm.creditengine.currency.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("unchecked")
class JdbcCurrencyServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-15T12:00:00Z");

    @Test
    void persistsTheClockTimestampRatherThanReadingSystemTime() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        service.recordObservation(" usd ", "brl", new BigDecimal("5.20"), "mock",
                NOW.minusSeconds(60), "admin@srm.local");

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(any(String.class), values.capture());
        assertThat(values.getValue()[1]).isEqualTo("USD");
        assertThat(values.getValue()[2]).isEqualTo("BRL");
        org.assertj.core.api.Assertions.assertThat(((java.sql.Timestamp) values.getValue()[6]).toInstant())
                .isEqualTo(NOW);
    }

    @Test
    void rejectsIncompleteObservationsWithControlledValidationErrors() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.recordObservation("USD", "BRL", new BigDecimal("5.20"), "",
                NOW, "admin@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate source is required");
    }

    @Test
    void rejectsUnsupportedCurrenciesBeforeIdentityOrLookup() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.resolveConversion(
                "EUR", "EUR", new BigDecimal("100.00"), NOW))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("The requested currency is not supported.");
        assertThatThrownBy(() -> service.observations("BRL", "EUR"))
                .isInstanceOf(UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> service.recordObservation(
                "EUR", "USD", new BigDecimal("1.10"), "provider", NOW, "admin@srm.local"))
                .isInstanceOf(UnsupportedCurrencyException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void canonicalizesIdentityConversionAndSettlementAmount() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        CurrencyService.Conversion result =
                service.resolveConversion(" brl ", "BRL", new BigDecimal("100"), NOW);

        assertThat(result.observation().base()).isEqualTo("BRL");
        assertThat(result.observation().quote()).isEqualTo("BRL");
        assertThat(result.observation().rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.observation().source()).isEqualTo("IDENTITY");
        assertThat(result.settlementAmount()).isEqualByComparingTo("100.00");
        assertThat(result.settlementAmount().scale()).isEqualTo(2);
        verifyNoInteractions(jdbc);
    }

    @Test
    void reportsMissingOnlyAfterCheckingDirectAndInverseRates() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(latestQuery(jdbc)).thenReturn(List.of(), List.of());
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.resolveConversion(
                "BRL", "USD", new BigDecimal("100.00"), NOW))
                .isInstanceOf(FxRateMissingException.class)
                .hasMessage("No exchange rate is available for the requested currency pair.");

        verify(jdbc, org.mockito.Mockito.times(2)).query(
                any(String.class), org.mockito.ArgumentMatchers.<RowMapper<CurrencyService.Observation>>any(),
                any(), any(), any());
    }

    @Test
    void acceptsDirectRateAtTheExactTwentyFourHourBoundary() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CurrencyService.Observation boundary = observation(
                "BRL", "USD", "0.20", NOW.minus(Duration.ofHours(24)));
        when(latestQuery(jdbc)).thenReturn(List.of(boundary), List.of());
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        CurrencyService.Conversion result =
                service.resolveConversion("brl", "usd", new BigDecimal("100.00"), NOW);

        assertThat(result.observation()).isEqualTo(boundary);
        assertThat(result.settlementAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void acceptsInverseRateAtTheExactTwentyFourHourBoundary() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CurrencyService.Observation boundary = observation(
                "USD", "BRL", "5.00", NOW.minus(Duration.ofHours(24)));
        when(latestQuery(jdbc)).thenReturn(List.of(), List.of(boundary));
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        CurrencyService.Conversion result =
                service.resolveConversion("BRL", "USD", new BigDecimal("100.00"), NOW);

        assertThat(result.observation()).isEqualTo(boundary);
        assertThat(result.settlementAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void usesFreshInverseWhenDirectRateIsStale() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CurrencyService.Observation staleDirect = observation(
                "BRL", "USD", "0.20", NOW.minus(Duration.ofHours(24)).minusNanos(1));
        CurrencyService.Observation freshInverse = observation(
                "USD", "BRL", "5.00", NOW.minusSeconds(1));
        when(latestQuery(jdbc)).thenReturn(List.of(staleDirect), List.of(freshInverse));
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        CurrencyService.Conversion result =
                service.resolveConversion("BRL", "USD", new BigDecimal("100.00"), NOW);

        assertThat(result.observation()).isEqualTo(freshInverse);
        assertThat(result.settlementAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void reportsStaleWhenNeitherOrientationIsFresh() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CurrencyService.Observation stale = observation(
                "BRL", "USD", "0.20", NOW.minus(Duration.ofHours(24)).minusNanos(1));
        when(latestQuery(jdbc)).thenReturn(List.of(stale), List.of());
        var service = new JdbcCurrencyService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.resolveConversion(
                "BRL", "USD", new BigDecimal("100.00"), NOW))
                .isInstanceOf(FxRateStaleException.class)
                .hasMessage("No fresh exchange rate is available for the requested currency pair.");
    }

    private static List<CurrencyService.Observation> latestQuery(JdbcTemplate jdbc) {
        return jdbc.query(
                any(String.class), org.mockito.ArgumentMatchers.<RowMapper<CurrencyService.Observation>>any(),
                any(), any(), any());
    }

    private static CurrencyService.Observation observation(
            String base, String quote, String rate, Instant observedAt) {
        return new CurrencyService.Observation(
                base, quote, new BigDecimal(rate), "provider", observedAt);
    }
}
