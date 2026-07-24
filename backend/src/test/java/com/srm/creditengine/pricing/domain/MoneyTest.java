package com.srm.creditengine.pricing.domain;

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

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("0.00"), "BRL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), "EUR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported currency");
    }
}
