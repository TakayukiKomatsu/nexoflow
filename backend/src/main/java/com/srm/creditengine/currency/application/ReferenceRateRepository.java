package com.srm.creditengine.currency.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ReferenceRateRepository {
    void recordBaseRate(String currency, BigDecimal monthlyRate, Instant effectiveAt);

    List<ReferenceRateService.BaseRate> baseRates(String currency, Instant effectiveAt);

    void recordProductSpread(String productType, BigDecimal monthlySpread, Instant effectiveAt);

    List<ReferenceRateService.ProductSpread> productSpreads(String productType, Instant effectiveAt);
}
