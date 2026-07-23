package com.srm.creditengine.currency.domain;

import java.math.BigDecimal;
import java.time.Instant;

public final class ReferenceRatePolicy {
    private static final BigDecimal MAX_MONTHLY_RATE = new BigDecimal("1.0000000000");

    private ReferenceRatePolicy() {
    }

    public static void validate(BigDecimal value, Instant effectiveAt, String actor) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("A reference rate must be positive");
        }
        if (value.compareTo(MAX_MONTHLY_RATE) > 0) {
            throw new IllegalArgumentException("A reference rate must be at most 1.0000000000");
        }
        if (effectiveAt == null) {
            throw new IllegalArgumentException("Reference rate effective time is required");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Actor is required");
        }
    }
}
