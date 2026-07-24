package com.srm.creditengine.pricing.application;

import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.pricing.domain.PricingQuoteSnapshot;
import com.srm.creditengine.pricing.domain.PricingStrategy;
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
import java.util.Objects;
import java.util.UUID;

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
    private final PricingAuditRecorder audit;

    AuthoritativePricingService(
            ReferenceRateService references,
            CurrencyService currency,
            PricingStrategyRegistry strategies,
            PricingQuoteRepository quotes,
            ReceivableQuoteReader receivables,
            Clock clock,
            FinancialTelemetry telemetry,
            PricingAuditRecorder audit) {
        this.references = Objects.requireNonNull(references, "references");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.strategies = Objects.requireNonNull(strategies, "strategies");
        this.quotes = Objects.requireNonNull(quotes, "quotes");
        this.receivables = Objects.requireNonNull(receivables, "receivables");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.audit = Objects.requireNonNull(audit, "audit");
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
        var timing = telemetry.startQuote();
        try {
            return createQuoteUnchecked(receivableId, settlementCurrency, actor);
        } catch (RuntimeException exception) {
            telemetry.quote("UNKNOWN", settlementCurrency, "rejected");
            throw exception;
        } finally {
            telemetry.completeQuote(timing);
        }
    }

    private Quote createQuoteUnchecked(UUID receivableId, String settlementCurrency, String actor) {
        var receivable = receivables
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
        quotes.save(snapshot, actor);
        var persisted = quotes
                .findById(snapshot.id())
                .orElseThrow(() -> new IllegalStateException("Pricing quote was not persisted"));
        audit.recordQuoteCreated(actor, persisted);
        telemetry.quote(receivable.productType(), settlementCurrency, "success");
        return toQuote(persisted, clock.instant());
    }

    @Override
    public Quote getQuote(UUID quoteId) {
        return quotes
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
        BigDecimal persistedDiscounted = monetaryBoundary(discounted, 4, "Discounted amount");
        var fx = currency.resolveConversion(input.faceCurrency(), input.settlementCurrency(), discounted, at);
        BigDecimal settlementAmount = monetaryBoundary(fx.settlementAmount(), 2, "Settlement amount");
        return new Breakdown(
                input.faceAmount().setScale(4, RoundingMode.HALF_EVEN),
                input.faceCurrency(),
                input.settlementCurrency(),
                base,
                spread,
                strategy.code(),
                "ACTUAL_DAYS_30_MONTH",
                term,
                persistedDiscounted,
                fx.observation().base(),
                fx.observation().quote(),
                fx.observation().rate(),
                fx.observation().source(),
                fx.observation().observedAt(),
                settlementAmount,
                at);
    }

    private static BigDecimal monetaryBoundary(BigDecimal value, int scale, String name) {
        BigDecimal rounded = value.setScale(scale, RoundingMode.HALF_EVEN);
        int integerDigits = Math.max(0, rounded.precision() - rounded.scale());
        if (rounded.signum() <= 0 || integerDigits > 15) {
            throw new IllegalArgumentException(
                    name + " must round to a positive value within 15 integer digits");
        }
        return rounded;
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

}
