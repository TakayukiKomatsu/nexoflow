package com.srm.creditengine.currency.domain;

import com.srm.creditengine.currency.FxConversionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/** Pure rate-selection and decimal-rounding policy for a conversion instant. */
public final class FxConversionPolicy {
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final FxConversionService CONVERSION = new FxConversionService();

    private FxConversionPolicy() {
    }

    public static Resolution resolve(
            FxObservation direct, FxObservation inverse, BigDecimal amount, Instant at) {
        if (direct != null && direct.base().equals(direct.quote())) {
            BigDecimal raw = CONVERSION.identity(amount);
            return new Resolution(direct, raw, raw.setScale(2, RoundingMode.HALF_EVEN));
        }
        if (isFresh(direct, at)) {
            BigDecimal raw = CONVERSION.direct(amount, direct.rate());
            return new Resolution(direct, raw, raw.setScale(2, RoundingMode.HALF_EVEN));
        }
        if (isFresh(inverse, at)) {
            BigDecimal raw = CONVERSION.inverse(amount, inverse.rate());
            return new Resolution(inverse, raw, raw.setScale(2, RoundingMode.HALF_EVEN));
        }
        if (direct == null && inverse == null) {
            throw new FxRateMissingException();
        }
        throw new FxRateStaleException();
    }

    private static boolean isFresh(FxObservation observation, Instant at) {
        return observation != null && !observation.observedAt().isBefore(at.minus(MAX_AGE));
    }

    public record Resolution(FxObservation observation, BigDecimal unroundedAmount, BigDecimal settlementAmount) {
    }
}
