package com.srm.creditengine.currency.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReferenceRateServiceTest {
    @Autowired ReferenceRateService references;

    @Test
    void seededBaseRateAndLaterVersionsAreSelectedInDeterministicEffectiveOrder() {
        Instant firstEffectiveAt = Instant.parse("2030-02-01T00:00:00Z");
        references.recordBaseRate("BRL", new BigDecimal("0.0200000000"), firstEffectiveAt);

        var rates = references.baseRates("BRL", Instant.parse("2030-02-15T00:00:00Z"));

        assertThat(rates).extracting(ReferenceRateService.BaseRate::monthlyRate)
                .containsExactly(new BigDecimal("0.0200000000"), new BigDecimal("0.0100000000"));
    }
}
