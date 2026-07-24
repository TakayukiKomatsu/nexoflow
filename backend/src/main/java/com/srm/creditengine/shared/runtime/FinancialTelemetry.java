package com.srm.creditengine.shared.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single metric seam for financial workflows. Its interface accepts only
 * bounded domain values; identifiers and request data are intentionally absent.
 */
@Component
public class FinancialTelemetry {
    private static final Set<String> PRODUCT_TYPES =
            Set.of("MERCANTILE_INVOICE", "POST_DATED_CHEQUE");
    private static final Set<String> CURRENCIES = Set.of("BRL", "USD");
    private static final Set<String> SIMULATION_RESULTS = Set.of("SUCCESS", "REJECTED");
    private static final Set<String> QUOTE_RESULTS = Set.of("SUCCESS", "REJECTED");
    private static final Set<String> PREVIEW_RESULTS = Set.of("SUCCESS", "REJECTED");
    private static final Set<String> SETTLEMENT_RESULTS = Set.of("SUCCESS", "CONFLICT");
    private static final Set<String> REVERSAL_RESULTS = Set.of("SUCCESS", "CONFLICT");
    private static final Set<String> REPORT_RESULTS = Set.of("SUCCESS", "REJECTED");
    private static final Set<String> FX_RESULTS = Set.of("SUCCESS", "UNAVAILABLE", "REJECTED");
    private static final Set<String> FX_ATTEMPT_RESULTS =
            Set.of("SUCCESS", "TRANSIENT_FAILURE", "PERMANENT_FAILURE");
    private final MeterRegistry registry;

    public FinancialTelemetry(MeterRegistry registry) {
        this.registry = registry;
        registerMandatoryMeters();
    }

    public void simulation(String productType, String settlementCurrency, String result) {
        increment("srm_simulation_outcomes_total",
                "product", bounded(productType, PRODUCT_TYPES),
                "currency", bounded(settlementCurrency, CURRENCIES),
                "result", bounded(result, SIMULATION_RESULTS));
    }

    public void quote(String productType, String settlementCurrency, String result) {
        increment("srm_quote_outcomes_total",
                "product", bounded(productType, PRODUCT_TYPES),
                "currency", bounded(settlementCurrency, CURRENCIES),
                "result", bounded(result, QUOTE_RESULTS));
    }

    public void preview(String settlementCurrency, String result) {
        increment("srm_preview_outcomes_total",
                "currency", bounded(settlementCurrency, CURRENCIES),
                "result", bounded(result, PREVIEW_RESULTS));
    }

    public void settlement(String settlementCurrency, String result) {
        increment("srm_settlement_outcomes_total",
                "currency", bounded(settlementCurrency, CURRENCIES),
                "result", bounded(result, SETTLEMENT_RESULTS));
    }

    public void reversal(String result) {
        increment("srm_reversal_outcomes_total", "result", bounded(result, REVERSAL_RESULTS));
    }

    public void report(String result) {
        increment("srm_statement_queries_total", "result", bounded(result, REPORT_RESULTS));
    }

    public void fx(String result) {
        increment("srm_fx_resilience_outcomes_total", "result", bounded(result, FX_RESULTS));
    }

    public void staleRate(String base, String quote) {
        increment(
                "srm_fx_stale_rates_total",
                "base",
                bounded(base, CURRENCIES),
                "quote",
                bounded(quote, CURRENCIES));
    }

    public void fxProviderRequest() {
        increment("srm.fx.provider.requests");
    }

    public void fxExternalFailure() {
        increment("srm.fx.provider.failures");
    }

    public Timer.Sample startQuote() {
        return Timer.start(registry);
    }

    public void completeQuote(Timer.Sample sample) {
        sample.stop(registry.timer("srm_quote_duration"));
    }

    public Timer.Sample startSettlement() {
        return Timer.start(registry);
    }

    public void completeSettlement(Timer.Sample sample) {
        sample.stop(registry.timer("srm_settlement_duration"));
    }

    public Timer.Sample startReport() {
        return Timer.start(registry);
    }

    public void completeReport(Timer.Sample sample) {
        sample.stop(registry.timer("srm_report_duration"));
    }

    public Timer.Sample startFxAttempt() {
        return Timer.start(registry);
    }

    public void completeFxAttempt(Timer.Sample attempt, String result) {
        attempt.stop(registry.timer("srm_fx_provider_attempt_duration",
                "result", bounded(result, FX_ATTEMPT_RESULTS)));
    }

    private void increment(String name, String... tags) {
        registry.counter(name, tags).increment();
    }

    private void registerMandatoryMeters() {
        registry.timer("srm_quote_duration");
        registry.timer("srm_settlement_duration");
        registry.timer("srm_report_duration");
        registry.timer("srm_fx_provider_attempt_duration", "result", "UNKNOWN");
        registry.counter(
                "srm_quote_outcomes_total",
                "product",
                "UNKNOWN",
                "currency",
                "UNKNOWN",
                "result",
                "REJECTED");
        registry.counter(
                "srm_simulation_outcomes_total",
                "product",
                "UNKNOWN",
                "currency",
                "UNKNOWN",
                "result",
                "REJECTED");
        registry.counter(
                "srm_settlement_outcomes_total",
                "currency",
                "UNKNOWN",
                "result",
                "CONFLICT");
        registry.counter(
                "srm_fx_stale_rates_total", "base", "UNKNOWN", "quote", "UNKNOWN");
        registry.counter("srm.fx.provider.failures");
        registry.counter("srm_fx_resilience_outcomes_total", "result", "UNAVAILABLE");
        registry.counter("srm_statement_queries_total", "result", "REJECTED");
    }

    private static String bounded(String value, Set<String> allowed) {
        if (value == null) return "UNKNOWN";
        String normalized = value.toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : "UNKNOWN";
    }
}
