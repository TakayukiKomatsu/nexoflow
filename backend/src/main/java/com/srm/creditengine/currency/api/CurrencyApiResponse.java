package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.application.CurrencyService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

final class CurrencyApiResponse {
    private CurrencyApiResponse() {}

    static Observation observation(CurrencyService.Observation value) {
        return new Observation(
                value.base(),
                value.quote(),
                value.rate().toPlainString(),
                value.source(),
                value.observedAt());
    }

    static Conversion conversion(CurrencyService.Conversion value) {
        return new Conversion(
                observation(value.observation()),
                value.unroundedConvertedAmount().toPlainString(),
                value.settlementAmount().toPlainString());
    }

    @Schema(
            name = "Observation",
            requiredProperties = {"base", "quote", "rate", "source", "observedAt"})
    record Observation(String base, String quote, String rate, String source, Instant observedAt) {}

    @Schema(
            name = "Conversion",
            requiredProperties = {"observation", "unroundedConvertedAmount", "settlementAmount"})
    record Conversion(
            Observation observation,
            String unroundedConvertedAmount,
            String settlementAmount) {}
}
