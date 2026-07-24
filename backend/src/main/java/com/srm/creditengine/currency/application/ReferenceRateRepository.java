package com.srm.creditengine.currency.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReferenceRateRepository {
    void recordBaseRate(
            UUID id, String currency, BigDecimal monthlyRate, Instant effectiveAt, String actor);

    List<ReferenceRateService.BaseRate> baseRates(String currency, Instant effectiveAt);

    void recordProductSpread(
            UUID id, String productType, BigDecimal monthlySpread, Instant effectiveAt, String actor);

    List<ReferenceRateService.ProductSpread> productSpreads(String productType, Instant effectiveAt);
}
