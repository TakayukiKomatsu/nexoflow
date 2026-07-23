package com.srm.creditengine.currency.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Versioned pricing reference data; historical rows are append-only. */
public interface ReferenceRateService {
    void recordBaseRate(String currency, BigDecimal monthlyRate, Instant effectiveAt, String actor);
    List<BaseRate> baseRates(String currency, Instant effectiveAt);
    void recordProductSpread(String productType, BigDecimal monthlySpread, Instant effectiveAt, String actor);
    List<ProductSpread> productSpreads(String productType, Instant effectiveAt);

    record BaseRate(String currency, BigDecimal monthlyRate, Instant effectiveAt, String createdBy) {}
    record ProductSpread(String productType, BigDecimal monthlySpread, Instant effectiveAt, String createdBy) {}
}
