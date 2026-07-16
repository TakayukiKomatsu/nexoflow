package com.srm.creditengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void addsAmountsOnlyInTheSameCurrency() {
        Money result = new Money(new BigDecimal("10.00"), "BRL").add(new Money(new BigDecimal("2.50"), "BRL"));
        assertThat(result.amount()).isEqualByComparingTo("12.50");
    }

    @Test
    void rejectsCurrencyMismatch() {
        Money brl = new Money(new BigDecimal("10.00"), "BRL");
        assertThatThrownBy(() -> brl.add(new Money(new BigDecimal("2.00"), "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
