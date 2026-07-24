package com.srm.creditengine.currency.application;

import com.srm.creditengine.currency.domain.FxObservation;
import java.time.Instant;
import java.util.UUID;

/** Persists the append-only audit event associated with an exchange-rate mutation. */
public interface ExchangeRateAuditRecorder {
    void record(String actor, UUID targetId, FxObservation observation, Instant occurredAt);
}
