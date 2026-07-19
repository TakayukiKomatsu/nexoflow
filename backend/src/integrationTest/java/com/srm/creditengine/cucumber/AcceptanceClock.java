package com.srm.creditengine.cucumber;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Mutable only from Cucumber steps; resets before every scenario. */
final class AcceptanceClock extends Clock {
    static final Instant INITIAL_INSTANT = Instant.parse("2030-01-15T12:00:00Z");

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    AcceptanceClock() {
        this(new AtomicReference<>(INITIAL_INSTANT), ZoneOffset.UTC);
    }

    private AcceptanceClock(AtomicReference<Instant> instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    void reset() {
        instant.set(INITIAL_INSTANT);
    }

    void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId requestedZone) {
        Objects.requireNonNull(requestedZone, "requestedZone");
        return zone.equals(requestedZone) ? this : new AcceptanceClock(instant, requestedZone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
