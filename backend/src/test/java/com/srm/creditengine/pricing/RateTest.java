package com.srm.creditengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RateTest {
    @Test
    void combinesBaseRateAndRiskSpread() {
        assertThat(new Rate(new BigDecimal("0.010")).add(new Rate(new BigDecimal("0.015"))).value())
                .isEqualByComparingTo("0.025");
    }

    @Test
    void rejectsNonPositiveRate() {
        assertThatThrownBy(() -> new Rate(new BigDecimal("-0.001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rate(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
