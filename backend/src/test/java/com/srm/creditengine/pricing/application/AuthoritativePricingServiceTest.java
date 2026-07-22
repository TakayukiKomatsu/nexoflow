package com.srm.creditengine.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.pricing.*;
import com.srm.creditengine.pricing.infrastructure.JdbcPricingQuoteRepository;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import com.srm.creditengine.receivable.application.ReceivableService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthoritativePricingServiceTest {
    @Test
    void PRICE_001_serverSimulatesInvoiceWithoutPersistingAQuote() {
        Instant now = Instant.parse("2030-01-15T12:00:00Z");
        ReferenceRateService rates = new ReferenceRateService() {
            public void recordBaseRate(String c, BigDecimal r, Instant e) {} public void recordProductSpread(String p, BigDecimal r, Instant e) {}
            public List<BaseRate> baseRates(String c, Instant e) { return List.of(new BaseRate("BRL",new BigDecimal("0.010"),now)); }
            public List<ProductSpread> productSpreads(String p, Instant e) { return List.of(new ProductSpread(p,new BigDecimal("0.015"),now)); }
        };
        CurrencyService currency = new CurrencyService() {
            public void recordObservation(String a,String b,BigDecimal r,String s,Instant o,String actor) {} public List<Observation> observations(String a,String b) { return List.of(); }
            public Conversion resolveConversion(String a,String b,BigDecimal amount,Instant at) { return new Conversion(new Observation(a,b,BigDecimal.ONE,"IDENTITY",at),amount,amount.setScale(2,java.math.RoundingMode.HALF_EVEN)); }
        };
        var registry = new PricingStrategyRegistry(List.of(new InvoicePricingStrategy(), new ChequePricingStrategy()));
        var service = new AuthoritativePricingService(rates,currency,registry,null,null,Clock.fixed(now,ZoneOffset.UTC));

        var result = service.simulate(new PricingService.Input(new BigDecimal("1000.00"),"BRL","MERCANTILE_INVOICE",LocalDate.parse("2030-02-14"),"BRL"));

        assertThat(result.termInMonths()).isEqualByComparingTo("1.0000000000");
        assertThat(result.settlementAmount()).isEqualByComparingTo("975.61");
        assertThat(result.dayCountConvention()).isEqualTo("ACTUAL_DAYS_30_MONTH");
    }
    @Test
    void orchestrationDelegatesSpreadSelectionAndDiscountBehaviorToTheStrategy() {
        Instant now = Instant.parse("2030-01-15T12:00:00Z");
        ReferenceRateService references = mock(ReferenceRateService.class);
        when(references.baseRates("BRL", now))
                .thenReturn(List.of(new ReferenceRateService.BaseRate(
                        "BRL", new BigDecimal("0.010"), now)));
        PricingStrategy strategy = mock(PricingStrategy.class);
        when(strategy.productType()).thenReturn("MERCANTILE_INVOICE");
        when(strategy.code()).thenReturn("OWNED_STRATEGY");
        var selectedSpread = new ReferenceRateService.ProductSpread(
                "MERCANTILE_INVOICE", new BigDecimal("0.015"), now);
        when(strategy.riskSpread(references, now)).thenReturn(selectedSpread);
        BigDecimal discounted = new BigDecimal("975.6097560975609756097560975610");
        when(strategy.discount(
                        new BigDecimal("1000.00"),
                        new BigDecimal("0.010"),
                        new BigDecimal("0.015"),
                        new BigDecimal("1.0000000000")))
                .thenReturn(discounted);
        CurrencyService currency = new CurrencyService() {
            public void recordObservation(
                    String base,
                    String quote,
                    BigDecimal rate,
                    String source,
                    Instant observedAt,
                    String actor) {}

            public List<Observation> observations(String base, String quote) {
                return List.of();
            }

            public Conversion resolveConversion(
                    String base, String quote, BigDecimal amount, Instant at) {
                return new Conversion(
                        new Observation(base, quote, BigDecimal.ONE, "IDENTITY", at),
                        amount,
                        amount.setScale(2, java.math.RoundingMode.HALF_EVEN));
            }
        };
        var service = new AuthoritativePricingService(
                references,
                currency,
                new PricingStrategyRegistry(List.of(strategy)),
                null,
                null,
                Clock.fixed(now, ZoneOffset.UTC));

        var result = service.simulate(new PricingService.Input(
                new BigDecimal("1000.00"),
                "BRL",
                "MERCANTILE_INVOICE",
                LocalDate.parse("2030-02-14"),
                "BRL"));

        assertThat(result.spread()).isEqualByComparingTo("0.015");
        assertThat(result.strategyCode()).isEqualTo("OWNED_STRATEGY");
        verify(references).baseRates("BRL", now);
        verifyNoMoreInteractions(references);
        verify(strategy).riskSpread(references, now);
        verify(strategy).discount(
                new BigDecimal("1000.00"),
                new BigDecimal("0.010"),
                new BigDecimal("0.015"),
                new BigDecimal("1.0000000000"));
    }

    @Test
    void rejectsUnsupportedProductBeforeAnyReferenceOrCurrencyLookup() {
        Instant now = Instant.parse("2030-01-15T12:00:00Z");
        ReferenceRateService references = mock(ReferenceRateService.class);
        CurrencyService currency = mock(CurrencyService.class);
        var registry =
                new PricingStrategyRegistry(List.of(new InvoicePricingStrategy(), new ChequePricingStrategy()));
        var service = new AuthoritativePricingService(
                references,
                currency,
                registry,
                null,
                null,
                Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.simulate(new PricingService.Input(
                        new BigDecimal("1000.00"),
                        "BRL",
                        "UNKNOWN",
                        LocalDate.parse("2030-02-14"),
                        "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported product type: UNKNOWN");
        verifyNoInteractions(references, currency);
    }
    @Test
    void quoteExpiryIsActiveAtFourteenMinutesFiftyNinePointNineNineNineSecondsAndExpiredAtTheBoundary() {
        Instant pricedAt = Instant.parse("2030-01-15T12:00:00Z");
        Instant expiresAt = pricedAt.plus(Duration.ofMinutes(15));
        JdbcTemplate jdbc = quoteSnapshot(pricedAt, expiresAt);

        assertThat(serviceAt(jdbc, expiresAt.minusMillis(1)).getQuote(QUOTE_ID).status())
                .isEqualTo("ACTIVE");
        assertThat(serviceAt(jdbc, expiresAt).getQuote(QUOTE_ID).status())
                .isEqualTo("EXPIRED");
        assertThat(serviceAt(jdbc, expiresAt.plusMillis(1)).getQuote(QUOTE_ID).status())
                .isEqualTo("EXPIRED");
    }

    private static final UUID QUOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID RECEIVABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    private static AuthoritativePricingService serviceAt(JdbcTemplate jdbc, Instant instant) {
        return new AuthoritativePricingService(
                null,
                null,
                null,
                new JdbcPricingQuoteRepository(jdbc),
                null,
                Clock.fixed(instant, ZoneOffset.UTC),
                new FinancialTelemetry(io.micrometer.core.instrument.Metrics.globalRegistry));
    }

    private static JdbcTemplate quoteSnapshot(Instant pricedAt, Instant expiresAt) {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:pricing-expiry-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create table pricing_quotes (
                    id uuid primary key,
                    receivable_id uuid not null,
                    product_type_code varchar(50) not null,
                    due_date date not null,
                    settlement_currency_code varchar(3) not null,
                    face_amount numeric(19,4) not null,
                    face_currency_code varchar(3) not null,
                    pricing_at timestamp not null,
                    expires_at timestamp not null,
                    base_rate numeric(19,10) not null,
                    spread numeric(19,10) not null,
                    strategy_code varchar(50) not null,
                    day_count_convention varchar(50) not null,
                    term_in_months numeric(19,10) not null,
                    discounted_amount numeric(19,4) not null,
                    fx_base_currency_code varchar(3) not null,
                    fx_quote_currency_code varchar(3) not null,
                    fx_rate numeric(19,10) not null,
                    fx_source varchar(100) not null,
                    fx_observed_at timestamp not null,
                    settlement_amount numeric(19,2) not null,
                    created_by varchar(200) not null,
                    status varchar(20) not null
                )
                """);
        jdbc.update(
                """
                insert into pricing_quotes (
                    id, receivable_id, product_type_code, due_date,
                    settlement_currency_code, face_amount, face_currency_code,
                    pricing_at, expires_at, base_rate, spread, strategy_code,
                    day_count_convention, term_in_months, discounted_amount,
                    fx_base_currency_code, fx_quote_currency_code, fx_rate,
                    fx_source, fx_observed_at, settlement_amount, created_by, status
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                QUOTE_ID,
                RECEIVABLE_ID,
                "MERCANTILE_INVOICE",
                java.sql.Date.valueOf("2030-02-14"),
                "BRL",
                new BigDecimal("1000.0000"),
                "BRL",
                java.sql.Timestamp.from(pricedAt),
                java.sql.Timestamp.from(expiresAt),
                new BigDecimal("0.0100000000"),
                new BigDecimal("0.0150000000"),
                "INVOICE_EXACT_V1",
                "ACTUAL_DAYS_30_MONTH",
                new BigDecimal("1.0000000000"),
                new BigDecimal("975.6098"),
                "BRL",
                "BRL",
                BigDecimal.ONE,
                "IDENTITY",
                java.sql.Timestamp.from(pricedAt),
                new BigDecimal("975.61"),
                "operator@srm.local",
                "ACTIVE");
        return jdbc;
    }
    @Test
    void rejectsEveryInvalidSimulationInputBeforeReferenceLookups() {
        Instant now = Instant.parse("2030-01-15T12:00:00Z");
        var service = new AuthoritativePricingService(
                null, null, null, null, null, Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.simulate(input(null, "BRL", "MERCANTILE_INVOICE", "2030-02-14", "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Face amount must be positive with no more than four decimal places");
        assertThatThrownBy(() -> service.simulate(input(BigDecimal.ZERO, "BRL", "MERCANTILE_INVOICE", "2030-02-14", "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Face amount must be positive with no more than four decimal places");
        assertThatThrownBy(() -> service.simulate(input(new BigDecimal("1.00001"), "BRL", "MERCANTILE_INVOICE", "2030-02-14", "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Face amount must be positive with no more than four decimal places");
        assertThatThrownBy(() -> service.simulate(input(BigDecimal.ONE, null, "MERCANTILE_INVOICE", "2030-02-14", "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pricing input is incomplete");
        assertThatThrownBy(() -> service.simulate(input(BigDecimal.ONE, "BRL", null, "2030-02-14", "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pricing input is incomplete");
        assertThatThrownBy(() -> service.simulate(input(BigDecimal.ONE, "BRL", "MERCANTILE_INVOICE", null, "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pricing input is incomplete");
        assertThatThrownBy(() -> service.simulate(input(BigDecimal.ONE, "BRL", "MERCANTILE_INVOICE", "2030-01-15", "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Due date must be after pricing date");
    }

    @Test
    void preservesNonIdentityRateAndTerminalStoredQuoteStatus() {
        Instant pricedAt = Instant.parse("2030-01-15T12:00:00Z");
        JdbcTemplate jdbc = quoteSnapshot(pricedAt, pricedAt.plus(Duration.ofMinutes(15)));
        jdbc.update(
                "update pricing_quotes set fx_source=?, fx_rate=?, status=?",
                "HTTP_PROVIDER",
                new BigDecimal("5.2500000000"),
                "CONSUMED");

        var quote = serviceAt(jdbc, pricedAt).getQuote(QUOTE_ID);

        assertThat(quote.breakdown().fxRate()).isEqualByComparingTo("5.2500000000");
        assertThat(quote.status()).isEqualTo("CONSUMED");
    }

    @Test
    void refusesToCreateAQuoteForNonRegisteredReceivables() {
        var reader = mock(ReceivableQuoteReader.class);
        when(reader.lockRegistered(RECEIVABLE_ID))
                .thenReturn(Optional.of(new ReceivableQuoteReader.LockedReceivable(
                        RECEIVABLE_ID,
                        "MERCANTILE_INVOICE",
                        new BigDecimal("1000.0000"),
                        "BRL",
                        LocalDate.parse("2030-02-14"),
                        "SETTLED")));
        var service = new AuthoritativePricingService(
                null,
                null,
                null,
                null,
                reader,
                Clock.fixed(Instant.parse("2030-01-15T12:00:00Z"), ZoneOffset.UTC),
                new FinancialTelemetry(io.micrometer.core.instrument.Metrics.globalRegistry));

        assertThatThrownBy(() -> service.createQuote(RECEIVABLE_ID, "BRL", "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only REGISTERED receivables can be quoted");
    }

    private static PricingService.Input input(
            BigDecimal faceAmount,
            String faceCurrency,
            String productType,
            String dueDate,
            String settlementCurrency) {
        return new PricingService.Input(
                faceAmount,
                faceCurrency,
                productType,
                dueDate == null ? null : LocalDate.parse(dueDate),
                settlementCurrency);
    }
}
