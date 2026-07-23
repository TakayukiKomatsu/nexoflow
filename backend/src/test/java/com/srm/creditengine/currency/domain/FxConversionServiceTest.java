package com.srm.creditengine.currency.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FxConversionServiceTest {
    @Test
    void directConversionMultipliesByRate() {
        assertThat(new FxConversionService().direct(new BigDecimal("100.00"), new BigDecimal("5.20")))
                .isEqualByComparingTo("520.0000000000");
    }

    @Test
    void sameCurrencyConversionPreservesAmount() {
        assertThat(new FxConversionService().identity(new BigDecimal("100.00")))
                .isEqualByComparingTo("100.0000000000");
    }

    @Test
    void inverseConversionDividesByRate() {
        assertThat(new FxConversionService().inverse(new BigDecimal("520.00"), new BigDecimal("5.20")))
                .isEqualByComparingTo("100.0000000000");
    }

    @Test
    void conversionRejectsNonPositiveRates() {
        var service = new FxConversionService();

        assertThatThrownBy(() -> service.direct(new BigDecimal("100.00"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate");
        assertThatThrownBy(() -> service.inverse(new BigDecimal("100.00"), new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate");
    }

    @Test
    void conversionRejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> new FxConversionService().identity(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }
}
