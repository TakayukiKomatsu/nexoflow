package com.srm.creditengine.currency.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

public final class ReferenceRatePolicy {
    private static final BigDecimal MAX_MONTHLY_RATE = new BigDecimal("1.0000000000");
    private static final Set<String> PRODUCT_TYPES =
            Set.of("MERCANTILE_INVOICE", "POST_DATED_CHEQUE");

    private ReferenceRatePolicy() {
    }

    public static void validate(BigDecimal value, Instant effectiveAt, String actor) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("A reference rate must be positive");
        }
        if (value.compareTo(MAX_MONTHLY_RATE) > 0) {
            throw new IllegalArgumentException("A reference rate must be at most 1.0000000000");
        }
        if (value.scale() > 10) {
            throw new IllegalArgumentException("A reference rate must have at most 10 fractional digits");
        }
        if (effectiveAt == null) {
            throw new IllegalArgumentException("Reference rate effective time is required");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Actor is required");
        }
        if (actor.length() > 320) {
            throw new IllegalArgumentException("Actor must not exceed 320 characters");
        }
    }

    public static String requireProductType(String productType) {
        String canonical = productType == null
                ? ""
                : productType.strip().toUpperCase(Locale.ROOT);
        if (!PRODUCT_TYPES.contains(canonical)) {
            throw new IllegalArgumentException("Unsupported product type");
        }
        return canonical;
    }
}
