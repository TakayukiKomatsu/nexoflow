package com.srm.creditengine.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.currency.domain.FxConversionService;
import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.pricing.domain.ChequePricingStrategy;
import com.srm.creditengine.pricing.domain.InvoicePricingStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingExactVectorTest {
    private static final Instant PRICED_AT = Instant.parse("2030-01-15T12:00:00Z");

    @Test
    void invoiceThirtyDayVectorEqualsIndependentlyCalculatedLiterals() {
        var result = service("0.010", Map.of("MERCANTILE_INVOICE", "0.015"), identity())
                .simulate(input("1000.00", "BRL", "MERCANTILE_INVOICE", "2030-02-14", "BRL"));

        assertThat(result.termInMonths()).isEqualByComparingTo("1.0000000000");
        assertThat(result.discountedAmount()).isEqualByComparingTo("975.6098");
        assertThat(result.settlementAmount()).isEqualByComparingTo("975.61");
    }

    @Test
    void chequeFortyFiveDayVectorUsesTheLiteralFractionalTermResult() {
        var result = service("0.010", Map.of("POST_DATED_CHEQUE", "0.025"), identity())
                .simulate(input("1000.00", "BRL", "POST_DATED_CHEQUE", "2030-03-01", "BRL"));

        assertThat(result.termInMonths()).isEqualByComparingTo("1.5000000000");
        assertThat(result.discountedAmount()).isEqualByComparingTo("949.7066");
        assertThat(result.settlementAmount()).isEqualByComparingTo("949.71");
    }

    @Test
    void directBrlToUsdVectorEqualsTheLiteralConvertedAmount() {
        var result = service("0.010", Map.of("MERCANTILE_INVOICE", "0.015"), direct("BRL", "USD", "0.2"))
                .simulate(input("1000.00", "BRL", "MERCANTILE_INVOICE", "2030-02-14", "USD"));

        assertThat(result.fxBaseCurrency()).isEqualTo("BRL");
        assertThat(result.fxQuoteCurrency()).isEqualTo("USD");
        assertThat(result.fxRate()).isEqualByComparingTo("0.2");
        assertThat(result.settlementAmount()).isEqualByComparingTo("195.12");
    }

    @Test
    void inverseUsdToBrlVectorEqualsTheLiteralConvertedAmount() {
        var result = service("0.010", Map.of("MERCANTILE_INVOICE", "0.015"), inverse("BRL", "USD", "0.2"))
                .simulate(input("1000.00", "USD", "MERCANTILE_INVOICE", "2030-02-14", "BRL"));

        assertThat(result.fxBaseCurrency()).isEqualTo("BRL");
        assertThat(result.fxQuoteCurrency()).isEqualTo("USD");
        assertThat(result.fxRate()).isEqualByComparingTo("0.2");
        assertThat(result.settlementAmount()).isEqualByComparingTo("4878.05");
    }

    @Test
    void settlementCurrencyHalfEvenTieRoundsToTheEvenCent() {
        var result = service("0", Map.of("MERCANTILE_INVOICE", "0"), identity())
                .simulate(input("10.005", "BRL", "MERCANTILE_INVOICE", "2030-02-14", "BRL"));

        assertThat(result.discountedAmount()).isEqualByComparingTo("10.0050");
        assertThat(result.settlementAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void acceptedRateCeilingUsesTheIndependentThreeToTheThreeHalvesVector() {
        var result = service("1.0000000000", Map.of("MERCANTILE_INVOICE", "1.0000000000"), identity())
                .simulate(input("1000.00", "BRL", "MERCANTILE_INVOICE", "2030-03-01", "BRL"));

        assertThat(result.termInMonths()).isEqualByComparingTo("1.5000000000");
        assertThat(result.discountedAmount()).isEqualByComparingTo("192.4501");
        assertThat(result.settlementAmount()).isEqualByComparingTo("192.45");
    }

    private static PricingService.Input input(
            String faceAmount, String faceCurrency, String productType, String dueDate, String settlementCurrency) {
        return new PricingService.Input(
                new BigDecimal(faceAmount), faceCurrency, productType, LocalDate.parse(dueDate), settlementCurrency);
    }

    private static PricingService service(
            String baseRate, Map<String, String> spreads, CurrencyService currency) {
        ReferenceRateService references = new ReferenceRateService() {
            @Override
            public void recordBaseRate(
                    String currency, BigDecimal rate, Instant effectiveAt, String actor) {}

            @Override
            public void recordProductSpread(
                    String productType, BigDecimal spread, Instant effectiveAt, String actor) {}

            @Override
            public List<BaseRate> baseRates(String currency, Instant effectiveAt) {
                return List.of(new BaseRate(
                        currency, new BigDecimal(baseRate), PRICED_AT, "fixture"));
            }

            @Override
            public List<ProductSpread> productSpreads(String productType, Instant effectiveAt) {
                return List.of(new ProductSpread(
                        productType,
                        new BigDecimal(spreads.get(productType)),
                        PRICED_AT,
                        "fixture"));
            }
        };
        return new AuthoritativePricingService(
                references,
                currency,
                new PricingStrategyRegistry(List.of(new InvoicePricingStrategy(), new ChequePricingStrategy())),
                null,
                null,
                Clock.fixed(PRICED_AT, ZoneOffset.UTC));
    }

    private static CurrencyService identity() {
        return conversionService((conversion, amount, rate) -> conversion.identity(amount), "IDENTITY", "BRL", "BRL", "1");
    }

    private static CurrencyService direct(String base, String quote, String rate) {
        return conversionService((conversion, amount, parsedRate) -> conversion.direct(amount, parsedRate), "DIRECT", base, quote, rate);
    }

    private static CurrencyService inverse(String base, String quote, String rate) {
        return conversionService((conversion, amount, parsedRate) -> conversion.inverse(amount, parsedRate), "INVERSE", base, quote, rate);
    }

    private static CurrencyService conversionService(
            ConversionOperation operation, String source, String observedBase, String observedQuote, String rate) {
        return new CurrencyService() {
            private final FxConversionService conversion = new FxConversionService();

            @Override
            public void recordObservation(
                    String base, String quote, BigDecimal value, String provider, Instant observedAt, String actor) {}

            @Override
            public List<Observation> observations(String base, String quote) {
                return List.of();
            }

            @Override
            public Conversion resolveConversion(
                    String requestedBase, String requestedQuote, BigDecimal amount, Instant at) {
                BigDecimal parsedRate = new BigDecimal(rate);
                BigDecimal raw = operation.convert(conversion, amount, parsedRate);
                return new Conversion(
                        new Observation(observedBase, observedQuote, parsedRate, source, at),
                        raw,
                        raw.setScale(2, RoundingMode.HALF_EVEN));
            }
        };
    }

    @FunctionalInterface
    private interface ConversionOperation {
        BigDecimal convert(FxConversionService conversion, BigDecimal amount, BigDecimal rate);
    }
}
