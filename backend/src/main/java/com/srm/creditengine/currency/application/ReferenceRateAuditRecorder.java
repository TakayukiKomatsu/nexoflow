package com.srm.creditengine.currency.application;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit boundary for pricing reference-data changes. */
public interface ReferenceRateAuditRecorder {
    void record(String actor, String action, String targetType, UUID targetId, Instant occurredAt);
}
