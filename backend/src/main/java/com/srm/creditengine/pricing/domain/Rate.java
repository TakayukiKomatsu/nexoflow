package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Rate(BigDecimal value) {
    public Rate {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("rate must be strictly positive");
        }
    }

    public Rate add(Rate other) {
        return new Rate(value.add(other.value));
    }
}
