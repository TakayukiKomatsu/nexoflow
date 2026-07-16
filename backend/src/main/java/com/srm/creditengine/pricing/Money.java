package com.srm.creditengine.pricing;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

public record Money(BigDecimal amount, String currency) {
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("BRL", "USD");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive");
        }
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("unsupported currency: " + currency);
        }
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
        return new Money(amount.add(other.amount), currency);
    }
}
