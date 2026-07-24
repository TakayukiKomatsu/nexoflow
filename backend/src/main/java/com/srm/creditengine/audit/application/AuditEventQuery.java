package com.srm.creditengine.audit.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read port for restricted, redacted audit-event inspection. */
public interface AuditEventQuery {
    List<Event> latest(int size);

    record Event(
            UUID id,
            String actor,
            String action,
            String targetType,
            UUID targetId,
            Instant occurredAt,
            String correlationId,
            String safeMetadata) {}
}
