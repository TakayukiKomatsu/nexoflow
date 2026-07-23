package com.srm.creditengine.currency.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReferenceRatePolicyTest {

    @Test
    void rejectsAMissingActor() {
        assertThatThrownBy(() -> ReferenceRatePolicy.validate(
                        new BigDecimal("0.0100000000"),
                        Instant.parse("2030-01-15T12:00:00Z"),
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Actor is required");
    }

    @Test
    void rejectsAMissingProductType() {
        assertThatThrownBy(() -> ReferenceRatePolicy.requireProductType(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported product type");
    }
}
