package com.srm.creditengine.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FxConversionServiceTest {
    @Test
    void directConversionMultipliesByRate() {
        assertThat(new FxConversionService().direct(new BigDecimal("100.00"), new BigDecimal("5.20")))
                .isEqualByComparingTo("520.0000000000");
    }

    @Test
    void inverseConversionDividesByRate() {
        assertThat(new FxConversionService().inverse(new BigDecimal("520.00"), new BigDecimal("5.20")))
                .isEqualByComparingTo("100.0000000000");
    }
}
