package com.srm.creditengine.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExactPricingStrategyBoundaryTest {
    private final PricingStrategy strategy = new InvoicePricingStrategy();

    @Test
    void zeroTermPreservesTheFaceAmountAtExtremeDecimalMagnitude() {
        BigDecimal faceAmount = new BigDecimal("123.4567");

        BigDecimal discounted = strategy.discount(
                faceAmount,
                new BigDecimal("1E+100"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(discounted).isEqualByComparingTo(faceAmount);
    }

    @Test
    void combinedRateAndTermCeilingsRemainFiniteAndPositive() {
        BigDecimal discounted = strategy.discount(
                new BigDecimal("1000.00"),
                new BigDecimal("1.0000000000"),
                new BigDecimal("1.0000000000"),
                new BigDecimal("121.7333333333"));

        assertThat(discounted)
                .isPositive()
                .isLessThan(new BigDecimal("1E-50"));
    }
}
