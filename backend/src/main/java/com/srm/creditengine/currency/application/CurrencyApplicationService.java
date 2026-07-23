package com.srm.creditengine.currency.application;

import com.srm.creditengine.currency.domain.SupportedCurrency;
import com.srm.creditengine.currency.domain.FxConversionPolicy;
import com.srm.creditengine.currency.domain.FxObservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrencyApplicationService implements CurrencyService {
    private final ExchangeRateRepository rates;
    private final ExchangeRateAuditRecorder audit;
    private final Clock clock;

    public CurrencyApplicationService(
            ExchangeRateRepository rates, ExchangeRateAuditRecorder audit, Clock clock) {
        this.rates = Objects.requireNonNull(rates, "rates");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void recordObservation(
            String base, String quote, BigDecimal rate, String source, Instant observedAt, String actor) {
        String canonicalBase = SupportedCurrency.require(base);
        String canonicalQuote = SupportedCurrency.require(quote);
        validateObservation(canonicalBase, canonicalQuote, rate, source, observedAt, actor);
        UUID id = UUID.randomUUID();
        Instant recordedAt = clock.instant();
        FxObservation observation = new FxObservation(canonicalBase, canonicalQuote, rate, source, observedAt);
        rates.record(id, observation, actor, recordedAt);
        audit.record(actor, id, observation, recordedAt);
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
        if (source.length() > 50) throw new IllegalArgumentException("Rate source must not exceed 50 characters");
        if (observedAt == null) throw new IllegalArgumentException("Rate observation time is required");
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Actor is required");
        if (actor.length() > 320) throw new IllegalArgumentException("Actor must not exceed 320 characters");
    }
}
