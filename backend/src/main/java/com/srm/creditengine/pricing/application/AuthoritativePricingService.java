package com.srm.creditengine.pricing.application;

import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.pricing.domain.PricingQuoteSnapshot;
import com.srm.creditengine.pricing.domain.PricingStrategy;
import com.srm.creditengine.receivable.application.ReceivableService;
import com.srm.creditengine.shared.domain.DomainResourceNotFoundException;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthoritativePricingService implements PricingService {
    private final ReferenceRateService references;
    private final CurrencyService currency;
    private final PricingStrategyRegistry strategies;
    private final PricingQuoteRepository quotes;
    private final ReceivableQuoteReader receivables;
    private final Clock clock;
    private final FinancialTelemetry telemetry;

    @Autowired
    AuthoritativePricingService(
            ReferenceRateService references,
            CurrencyService currency,
            PricingStrategyRegistry strategies,
            PricingQuoteRepository quotes,
            ReceivableQuoteReader receivables,
            Clock clock,
            FinancialTelemetry telemetry) {
        this.references = references;
        this.currency = currency;
        this.strategies = strategies;
        this.quotes = quotes;
        this.receivables = receivables;
        this.clock = clock;
        this.telemetry = telemetry;
    }

    // Retained for existing simulation-focused package tests; quote methods require typed ports.
    AuthoritativePricingService(
            ReferenceRateService references,
            CurrencyService currency,
            PricingStrategyRegistry strategies,
            ReceivableService ignored,
            Object ignoredRepository,
            Clock clock) {
        this(references, currency, strategies, null, null, clock,
                new FinancialTelemetry(io.micrometer.core.instrument.Metrics.globalRegistry));
    }

    @Override
    public Breakdown simulate(Input input) {
        try {
            Breakdown breakdown = calculate(input, clock.instant());
            telemetry.simulation(input.productType(), input.settlementCurrency(), "success");
            return breakdown;
        } catch (RuntimeException exception) {
            telemetry.simulation(input.productType(), input.settlementCurrency(), "rejected");
            throw exception;
        }
    }

    @Override
    @Transactional
    public Quote createQuote(UUID receivableId, String settlementCurrency, String actor) {
        try {
            return createQuoteUnchecked(receivableId, settlementCurrency, actor);
        } catch (RuntimeException exception) {
            telemetry.quote("UNKNOWN", settlementCurrency, "rejected");
            throw exception;
        }
    }

    private Quote createQuoteUnchecked(UUID receivableId, String settlementCurrency, String actor) {
        var receivable = requireQuoteReader()
                .lockRegistered(receivableId)
                .orElseThrow(DomainResourceNotFoundException::new);
        if (!"REGISTERED".equals(receivable.status())) {
            throw new IllegalArgumentException("Only REGISTERED receivables can be quoted");
        }
        Breakdown breakdown = calculate(
                new Input(
                        receivable.faceAmount(),
                        receivable.faceCurrency(),
                        receivable.productType(),
                        receivable.dueDate(),
                        settlementCurrency),
                clock.instant());
        Instant expiresAt = breakdown.pricedAt().plus(Duration.ofMinutes(15));
        var snapshot = new PricingQuoteSnapshot(
                UUID.randomUUID(),
                receivable.id(),
                receivable.productType(),
                receivable.dueDate(),
                breakdown.settlementCurrency(),
                breakdown.faceAmount(),
                breakdown.faceCurrency(),
                breakdown.pricedAt(),
                expiresAt,
                breakdown.baseRate(),
                breakdown.spread(),
                breakdown.strategyCode(),
                breakdown.dayCountConvention(),
                breakdown.termInMonths(),
                breakdown.discountedAmount(),
                breakdown.fxBaseCurrency(),
                breakdown.fxQuoteCurrency(),
                breakdown.fxRate(),
                breakdown.fxSource(),
                breakdown.fxObservedAt(),
                breakdown.settlementAmount(),
                actor,
                "ACTIVE");
        requireQuoteRepository().save(snapshot, actor);
        telemetry.quote(receivable.productType(), settlementCurrency, "success");
        return toQuote(snapshot, clock.instant());
    }

    @Override
    public Quote getQuote(UUID quoteId) {
        return requireQuoteRepository()
                .findById(quoteId)
                .map(snapshot -> toQuote(snapshot, clock.instant()))
                .orElseThrow(DomainResourceNotFoundException::new);
    }
    private Breakdown calculate(Input input, Instant at) {
        if (input.faceAmount() == null || input.faceAmount().signum() <= 0 || input.faceAmount().scale() > 4) {
            throw new IllegalArgumentException("Face amount must be positive with no more than four decimal places");
        }
        if (input.faceCurrency() == null || input.settlementCurrency() == null || input.productType() == null
                || input.dueDate() == null) {
            throw new IllegalArgumentException("Pricing input is incomplete");
        }
        LocalDate pricingDate = at.atZone(ZoneOffset.UTC).toLocalDate();
        if (!input.dueDate().isAfter(pricingDate)) {
            throw new IllegalArgumentException("Due date must be after pricing date");
        }
        if (input.dueDate().isAfter(pricingDate.plusYears(10))) {
            throw new IllegalArgumentException("Pricing term must not exceed ten years");
        }
        PricingStrategy strategy = strategies.forProduct(input.productType());
        BigDecimal base = effectiveBaseRate(input.faceCurrency(), at);
        BigDecimal spread = strategy.riskSpread(references.productSpreads(strategy.productType(), at).stream()
                .map(ReferenceRateService.ProductSpread::monthlySpread)
                .toList());
        BigDecimal term = BigDecimal.valueOf(ChronoUnit.DAYS.between(pricingDate, input.dueDate()))
                .divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_EVEN);
        BigDecimal discounted = strategy.discount(input.faceAmount(), base, spread, term);
        var fx = currency.resolveConversion(input.faceCurrency(), input.settlementCurrency(), discounted, at);
        return new Breakdown(
                input.faceAmount().setScale(4, RoundingMode.HALF_EVEN),
                input.faceCurrency(),
                input.settlementCurrency(),
                base,
                spread,
                strategy.code(),
                "ACTUAL_DAYS_30_MONTH",
                term,
                discounted.setScale(4, RoundingMode.HALF_EVEN),
                fx.observation().base(),
                fx.observation().quote(),
                fx.observation().rate(),
                fx.observation().source(),
                fx.observation().observedAt(),
                fx.settlementAmount(),
                at);
    }

    private static Quote toQuote(PricingQuoteSnapshot snapshot, Instant now) {
        BigDecimal effectiveFxRate = "IDENTITY".equals(snapshot.fxSource()) ? BigDecimal.ONE : snapshot.fxRate();
        var breakdown = new Breakdown(
                snapshot.faceAmount().setScale(4, RoundingMode.HALF_EVEN),
                snapshot.faceCurrency(),
                snapshot.settlementCurrency(),
                snapshot.baseRate(),
                snapshot.spread(),
                snapshot.strategyCode(),
                snapshot.dayCountConvention(),
                snapshot.termInMonths(),
                snapshot.discountedAmount().setScale(4, RoundingMode.HALF_EVEN),
                snapshot.fxBaseCurrency(),
                snapshot.fxQuoteCurrency(),
                effectiveFxRate,
                snapshot.fxSource(),
                snapshot.fxObservedAt(),
                snapshot.settlementAmount().setScale(2, RoundingMode.HALF_EVEN),
                snapshot.pricedAt());
        String status = "ACTIVE".equals(snapshot.status()) && !now.isBefore(snapshot.expiresAt())
                ? "EXPIRED"
                : snapshot.status();
        return new Quote(
                snapshot.id(),
                snapshot.receivableId(),
                snapshot.productType(),
                snapshot.dueDate(),
                breakdown,
                snapshot.expiresAt(),
                status,
                snapshot.createdBy());
    }

    private BigDecimal effectiveBaseRate(String currencyCode, Instant at) {
        return references.baseRates(currencyCode, at).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No effective base rate"))
                .monthlyRate();
    }

    private PricingQuoteRepository requireQuoteRepository() {
        if (quotes == null) throw new IllegalStateException("Pricing quote repository is unavailable");
        return quotes;
    }

    private ReceivableQuoteReader requireQuoteReader() {
        if (receivables == null) throw new IllegalStateException("Receivable quote reader is unavailable");
        return receivables;
    }
}
