package com.srm.creditengine.currency.application;

import com.srm.creditengine.currency.domain.SupportedCurrency;
import com.srm.creditengine.currency.domain.FxConversionPolicy;
import com.srm.creditengine.currency.domain.FxObservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrencyApplicationService implements CurrencyService {
    private final ExchangeRateRepository rates;
    private final Clock clock;

    public CurrencyApplicationService(ExchangeRateRepository rates, Clock clock) {
        this.rates = rates;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recordObservation(
            String base, String quote, BigDecimal rate, String source, Instant observedAt, String actor) {
        String canonicalBase = SupportedCurrency.require(base);
        String canonicalQuote = SupportedCurrency.require(quote);
        validateObservation(canonicalBase, canonicalQuote, rate, source, observedAt, actor);
        rates.record(new FxObservation(canonicalBase, canonicalQuote, rate, source, observedAt), actor, clock.instant());
    }

    @Override
    public List<Observation> observations(String base, String quote) {
        return rates.observations(SupportedCurrency.require(base), SupportedCurrency.require(quote)).stream()
                .map(observation -> new Observation(
                        observation.base(), observation.quote(), observation.rate(), observation.source(), observation.observedAt()))
                .toList();
    }

    @Override
    public Conversion resolveConversion(String base, String quote, BigDecimal amount, Instant at) {
        String canonicalBase = SupportedCurrency.require(base);
        String canonicalQuote = SupportedCurrency.require(quote);
        FxConversionPolicy.Resolution resolved;
        if (canonicalBase.equals(canonicalQuote)) {
            resolved = FxConversionPolicy.resolve(
                    new FxObservation(canonicalBase, canonicalQuote, BigDecimal.ONE, "IDENTITY", at), null, amount, at);
        } else {
            resolved = FxConversionPolicy.resolve(
                    rates.latest(canonicalBase, canonicalQuote, at).orElse(null),
                    rates.latest(canonicalQuote, canonicalBase, at).orElse(null),
                    amount,
                    at);
        }
        FxObservation observation = resolved.observation();
        return new Conversion(
                new Observation(observation.base(), observation.quote(), observation.rate(), observation.source(), observation.observedAt()),
                resolved.unroundedAmount(),
                resolved.settlementAmount());
    }

    private static void validateObservation(
            String base, String quote, BigDecimal rate, String source, Instant observedAt, String actor) {
        if (base.equals(quote)) throw new IllegalArgumentException("Base and quote currencies must differ");
        if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("A rate must be positive");
        if (rate.scale() > 10 || rate.precision() - rate.scale() > 9) {
            throw new IllegalArgumentException("A rate must have at most 9 integer and 10 fractional digits");
        }
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Rate source is required");
        if (observedAt == null) throw new IllegalArgumentException("Rate observation time is required");
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Actor is required");
    }
}
