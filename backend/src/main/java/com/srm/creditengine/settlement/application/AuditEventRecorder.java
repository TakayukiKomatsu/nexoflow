package com.srm.creditengine.settlement.application;

import java.time.Instant;
import java.util.UUID;

/** Append-only operational audit boundary for settlement state transitions. */
public interface AuditEventRecorder {
    void record(String actor, String action, String targetType, UUID targetId, Instant occurredAt, String safeMetadata);
}
