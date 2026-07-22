package com.srm.creditengine.currency.application;

import com.srm.creditengine.currency.infrastructure.JdbcReferenceRateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Test fixture retaining the old constructor while exercising the extracted adapter. */
final class JdbcReferenceRateService implements ReferenceRateService {
    private final JdbcReferenceRateRepository delegate;

    JdbcReferenceRateService(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        delegate = new JdbcReferenceRateRepository(jdbc);
    }

    @Override
    public void recordBaseRate(String currency, BigDecimal monthlyRate, Instant effectiveAt) {
        delegate.recordBaseRate(currency, monthlyRate, effectiveAt);
    }

    @Override
    public List<BaseRate> baseRates(String currency, Instant effectiveAt) {
        return delegate.baseRates(currency, effectiveAt);
    }

    @Override
    public void recordProductSpread(String productType, BigDecimal monthlySpread, Instant effectiveAt) {
        delegate.recordProductSpread(productType, monthlySpread, effectiveAt);
    }

    @Override
    public List<ProductSpread> productSpreads(String productType, Instant effectiveAt) {
        return delegate.productSpreads(productType, effectiveAt);
    }
}
