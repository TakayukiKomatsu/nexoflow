package com.srm.creditengine.currency.application;

import com.srm.creditengine.currency.infrastructure.JdbcExchangeRateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Test fixture retaining the old constructor while exercising the extracted production layers. */
final class JdbcCurrencyService implements CurrencyService {
    private final CurrencyApplicationService delegate;

    JdbcCurrencyService(org.springframework.jdbc.core.JdbcTemplate jdbc, Clock clock) {
        delegate = new CurrencyApplicationService(new JdbcExchangeRateRepository(jdbc), clock);
    }

    @Override
    public void recordObservation(String base, String quote, BigDecimal rate, String source, Instant observedAt, String actor) {
        delegate.recordObservation(base, quote, rate, source, observedAt, actor);
    }

    @Override
    public List<Observation> observations(String base, String quote) {
        return delegate.observations(base, quote);
    }

    @Override
    public Conversion resolveConversion(String base, String quote, BigDecimal amount, Instant at) {
        return delegate.resolveConversion(base, quote, amount, at);
    }
}
