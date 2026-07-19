package com.srm.creditengine.pricing.application;

import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.pricing.PricingStrategyRegistry;
import com.srm.creditengine.pricing.PricingStrategy;
import com.srm.creditengine.receivable.application.ReceivableService;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;

@Service
class AuthoritativePricingService implements PricingService {
    private final ReferenceRateService references; private final CurrencyService currency; private final PricingStrategyRegistry strategies; private final ReceivableService receivables; private final JdbcTemplate jdbc; private final Clock clock; private final FinancialTelemetry telemetry;
    @Autowired AuthoritativePricingService(ReferenceRateService references, CurrencyService currency, PricingStrategyRegistry strategies, ReceivableService receivables, JdbcTemplate jdbc, Clock clock, FinancialTelemetry telemetry) { this.references=references; this.currency=currency; this.strategies=strategies; this.receivables=receivables; this.jdbc=jdbc; this.clock=clock; this.telemetry=telemetry; }
    AuthoritativePricingService(ReferenceRateService references, CurrencyService currency, PricingStrategyRegistry strategies, ReceivableService receivables, JdbcTemplate jdbc, Clock clock) { this(references, currency, strategies, receivables, jdbc, clock, new FinancialTelemetry(io.micrometer.core.instrument.Metrics.globalRegistry)); }
    @Override public Breakdown simulate(Input input) { try { Breakdown breakdown = calculate(input, clock.instant()); telemetry.simulation(input.productType(), input.settlementCurrency(), "success"); return breakdown; } catch (RuntimeException exception) { telemetry.simulation(input.productType(), input.settlementCurrency(), "rejected"); throw exception; } }
    @Override
    @Transactional
    public Quote createQuote(UUID receivableId, String settlementCurrency, String actor) {
        var receivable = jdbc.query(
                        "select id,assignor_id,product_type_code,face_amount,face_currency_code,issue_date,due_date,status,version from receivables where id=? for update",
                        (rs, row) -> new ReceivableService.Receivable(
                                rs.getObject(1, UUID.class),
                                rs.getObject(2, UUID.class),
                                rs.getString(3),
                                rs.getBigDecimal(4),
                                rs.getString(5),
                                rs.getDate(6).toLocalDate(),
                                rs.getDate(7).toLocalDate(),
                                rs.getString(8),
                                rs.getLong(9)),
                        receivableId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Receivable not found"));
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
        UUID id = UUID.randomUUID();
        Instant expiresAt = breakdown.pricedAt().plus(Duration.ofMinutes(15));
        jdbc.update(
                """
                insert into pricing_quotes
                    (id, receivable_id, settlement_currency_code, face_amount, face_currency_code,
                     product_type_code, due_date, pricing_at, expires_at, base_rate, spread,
                     strategy_code, day_count_convention, term_in_months, discounted_amount,
                     fx_base_currency_code, fx_quote_currency_code, fx_rate, fx_source,
                     fx_observed_at, settlement_amount, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                receivableId,
                breakdown.settlementCurrency(),
                breakdown.faceAmount(),
                breakdown.faceCurrency(),
                receivable.productType(),
                java.sql.Date.valueOf(receivable.dueDate()),
                Timestamp.from(breakdown.pricedAt()),
                Timestamp.from(expiresAt),
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
                Timestamp.from(breakdown.fxObservedAt()),
                breakdown.settlementAmount(),
                actor);
        jdbc.update(
                "insert into audit_events (id,actor,action,target_type,target_id,occurred_at,safe_metadata) values (?,?,?,?,?,?,?::jsonb)",
                UUID.randomUUID(),
                actor,
                "QUOTE_CREATED",
                "PRICING_QUOTE",
                id,
                Timestamp.from(breakdown.pricedAt()),
                "{}");
        telemetry.quote(receivable.productType(), settlementCurrency, "success");
        return new Quote(
                id,
                receivableId,
                receivable.productType(),
                receivable.dueDate(),
                breakdown,
                expiresAt,
                "ACTIVE",
                actor);
    }

    @Override
    public Quote getQuote(UUID quoteId) {
        return jdbc.query(
                        """
                        select id, receivable_id, product_type_code, due_date,
                               settlement_currency_code, face_amount, face_currency_code,
                               pricing_at, expires_at, base_rate, spread, strategy_code,
                               day_count_convention, term_in_months, discounted_amount,
                               fx_base_currency_code, fx_quote_currency_code, fx_rate,
                               fx_source, fx_observed_at, settlement_amount, created_by, status
                        from pricing_quotes
                        where id = ?
                        """,
                        (rs, row) -> {
                            Instant pricedAt = rs.getTimestamp("pricing_at").toInstant();
                            Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
                            BigDecimal fxRate = rs.getBigDecimal("fx_rate");
                            if ("IDENTITY".equals(rs.getString("fx_source"))) {
                                fxRate = BigDecimal.ONE;
                            }
                            Breakdown breakdown = new Breakdown(
                                    rs.getBigDecimal("face_amount").setScale(4, RoundingMode.HALF_EVEN),
                                    rs.getString("face_currency_code"),
                                    rs.getString("settlement_currency_code"),
                                    rs.getBigDecimal("base_rate"),
                                    rs.getBigDecimal("spread"),
                                    rs.getString("strategy_code"),
                                    rs.getString("day_count_convention"),
                                    rs.getBigDecimal("term_in_months"),
                                    rs.getBigDecimal("discounted_amount").setScale(4, RoundingMode.HALF_EVEN),
                                    rs.getString("fx_base_currency_code"),
                                    rs.getString("fx_quote_currency_code"),
                                    fxRate,
                                    rs.getString("fx_source"),
                                    rs.getTimestamp("fx_observed_at").toInstant(),
                                    rs.getBigDecimal("settlement_amount").setScale(2, RoundingMode.HALF_EVEN),
                                    pricedAt);
                            String storedStatus = rs.getString("status");
                            String effectiveStatus = "ACTIVE".equals(storedStatus)
                                            && !clock.instant().isBefore(expiresAt)
                                    ? "EXPIRED"
                                    : storedStatus;
                            return new Quote(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("receivable_id", UUID.class),
                                    rs.getString("product_type_code"),
                                    rs.getDate("due_date").toLocalDate(),
                                    breakdown,
                                    expiresAt,
                                    effectiveStatus,
                                    rs.getString("created_by"));
                        },
                        quoteId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pricing quote not found"));
    }
    private Breakdown calculate(Input i, Instant at) {
        if (i.faceAmount()==null || i.faceAmount().signum()<=0 || i.faceAmount().scale() > 4) throw new IllegalArgumentException("Face amount must be positive with no more than four decimal places");
        if (i.faceCurrency()==null || i.settlementCurrency()==null || i.productType()==null || i.dueDate()==null) throw new IllegalArgumentException("Pricing input is incomplete");
        LocalDate pricingDate=at.atZone(ZoneOffset.UTC).toLocalDate(); if (!i.dueDate().isAfter(pricingDate)) throw new IllegalArgumentException("Due date must be after pricing date");
        PricingStrategy strategy=strategies.forProduct(i.productType()); BigDecimal base=effectiveBaseRate(i.faceCurrency(),at); BigDecimal spread=strategy.riskSpread(references,at).monthlySpread();
        BigDecimal term=BigDecimal.valueOf(ChronoUnit.DAYS.between(pricingDate,i.dueDate())).divide(BigDecimal.valueOf(30),10,RoundingMode.HALF_EVEN); BigDecimal discounted=strategy.discount(i.faceAmount(),base,spread,term); var fx=currency.resolveConversion(i.faceCurrency(),i.settlementCurrency(),discounted,at);
        return new Breakdown(i.faceAmount().setScale(4,RoundingMode.HALF_EVEN),i.faceCurrency(),i.settlementCurrency(),base,spread,strategy.code(),"ACTUAL_DAYS_30_MONTH",term,discounted.setScale(4,RoundingMode.HALF_EVEN),fx.observation().base(),fx.observation().quote(),fx.observation().rate(),fx.observation().source(),fx.observation().observedAt(),fx.settlementAmount(),at);
    }
    private BigDecimal effectiveBaseRate(String currencyCode, Instant at) {
        return references.baseRates(currencyCode, at).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No effective base rate"))
                .monthlyRate();
    }
}
