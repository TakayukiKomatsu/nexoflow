package com.srm.creditengine.shared.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class FinancialTelemetryTest {
    @Test
    void OBS_003_financialMetricsUseOnlyBoundedDomainLabels() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new FinancialTelemetry(registry);

        telemetry.settlement("BRL", "conflict");
        telemetry.simulation("MERCANTILE_INVOICE", "USD", "success");
        telemetry.simulation("ADMIN", "USD_BRL", "APPROVED");
        telemetry.preview("BRL", "rejected");
        telemetry.settlement("ADMIN", "APPROVED");
        telemetry.completeFxAttempt(telemetry.startFxAttempt(), "APPROVED");

        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getValue()).matches("[A-Z_]{2,32}")));
        assertThat(registry.find("srm_settlement_outcomes_total")
                .tags("currency", "UNKNOWN", "result", "UNKNOWN").counter()).isNotNull();
        assertThat(registry.find("srm_simulation_outcomes_total")
                .tags("product", "UNKNOWN", "currency", "UNKNOWN", "result", "UNKNOWN").counter()).isNotNull();
        assertThat(registry.find("srm_fx_provider_attempt_duration_seconds")
                .tag("result", "UNKNOWN").timer()).isNotNull();
        assertThat(registry.find("srm_preview_outcomes_total")
                .tags("currency", "BRL", "result", "REJECTED").counter()).isNotNull();
    }

    @Test
    void fxAttemptLatencyUsesOnlyTheThreeBoundedResults() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new FinancialTelemetry(registry);

        for (String result : new String[] {"success", "transient_failure", "permanent_failure"}) {
            telemetry.completeFxAttempt(telemetry.startFxAttempt(), result);
        }

        assertThat(registry.find("srm_fx_provider_attempt_duration_seconds").timers())
                .extracting(timer -> timer.getId().getTag("result"))
                .containsExactlyInAnyOrder("SUCCESS", "TRANSIENT_FAILURE", "PERMANENT_FAILURE");
    }
}
