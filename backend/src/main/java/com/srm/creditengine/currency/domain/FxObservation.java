package com.srm.creditengine.currency.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable market-rate observation used by the currency application boundary. */
public record FxObservation(String base, String quote, BigDecimal rate, String source, Instant observedAt) {
}
