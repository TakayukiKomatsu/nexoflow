package com.srm.creditengine.currency.infrastructure;

import com.srm.creditengine.currency.application.ReferenceRateService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Test fixture retaining the old constructor while exercising the extracted adapter. */
public final class JdbcReferenceRateService implements ReferenceRateService {
    private final JdbcReferenceRateRepository delegate;

    public JdbcReferenceRateService(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        delegate = new JdbcReferenceRateRepository(jdbc);
    }

    @Override public void recordBaseRate(String currency, BigDecimal rate, Instant at) { delegate.recordBaseRate(currency, rate, at); }
    @Override public List<BaseRate> baseRates(String currency, Instant at) { return delegate.baseRates(currency, at); }
    @Override public void recordProductSpread(String product, BigDecimal spread, Instant at) { delegate.recordProductSpread(product, spread, at); }
    @Override public List<ProductSpread> productSpreads(String product, Instant at) { return delegate.productSpreads(product, at); }
}
