package com.srm.creditengine.currency.application;

import java.time.Duration;

/** Isolates the blocking wait so retry behavior is deterministic and testable. */
@FunctionalInterface
interface FxRetrySleeper {
    void sleep(Duration delay) throws InterruptedException;

    static FxRetrySleeper system() {
        return delay -> Thread.sleep(delay.toMillis());
    }
}
