package com.srm.creditengine.currency.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class FxConversionService {
    public BigDecimal direct(BigDecimal amount, BigDecimal rate) {
        requirePositive(amount, "amount");
        requirePositive(rate, "rate");
        return amount.multiply(rate).setScale(10, RoundingMode.HALF_EVEN);
    }

    public BigDecimal identity(BigDecimal amount) {
        requirePositive(amount, "amount");
        return amount.setScale(10, RoundingMode.HALF_EVEN);
    }

    public BigDecimal inverse(BigDecimal amount, BigDecimal rate) {
        requirePositive(amount, "amount");
        requirePositive(rate, "rate");
        return amount.divide(rate, 10, RoundingMode.HALF_EVEN);
    }

    private void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be strictly positive");
        }
    }
}
