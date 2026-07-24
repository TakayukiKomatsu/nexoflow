package com.srm.creditengine.currency.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FxConversionPolicyTest {
    private static final Instant NOW = Instant.parse("2030-01-15T12:00:00Z");

    @Test
    void selectsFreshDirectRateBeforeFreshInverseRate() {
        var result = FxConversionPolicy.resolve(observation("BRL", "USD", "0.20", NOW),
                observation("USD", "BRL", "5.10", NOW), new BigDecimal("100.0000"), NOW);

        assertThat(result.settlementAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void usesFreshInverseRateWhenDirectRateIsUnavailable() {
        var result = FxConversionPolicy.resolve(null, observation("USD", "BRL", "5.00", NOW),
                new BigDecimal("100.0000"), NOW);

        assertThat(result.unroundedAmount()).isEqualByComparingTo("20.0000000000");
    }

    @Test
    void rejectsStaleRates() {
        assertThatThrownBy(() -> FxConversionPolicy.resolve(
                observation("BRL", "USD", "0.20", NOW.minusSeconds(86_401)),
                null,
                BigDecimal.ONE,
                NOW)).isInstanceOf(FxRateStaleException.class);
    }

    private static FxObservation observation(String base, String quote, String rate, Instant observedAt) {
        return new FxObservation(base, quote, new BigDecimal(rate), "TEST", observedAt);
    }
}
