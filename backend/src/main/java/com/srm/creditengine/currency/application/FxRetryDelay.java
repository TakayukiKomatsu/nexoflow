package com.srm.creditengine.currency.application;

import java.time.Duration;
import java.util.function.DoubleSupplier;

/** Supplies the bounded delay between failed provider attempts. */
@FunctionalInterface
interface FxRetryDelay {
    Duration afterFailure(int failedAttempt);

    static FxRetryDelay exponential(DoubleSupplier jitter) {
        return failedAttempt -> {
            long exponentialMillis = Math.min(1_000L, 100L << (failedAttempt - 1));
            double factor = 0.5d + Math.max(0d, Math.min(1d, jitter.getAsDouble()));
            return Duration.ofMillis(Math.round(exponentialMillis * factor));
        };
    }
}
