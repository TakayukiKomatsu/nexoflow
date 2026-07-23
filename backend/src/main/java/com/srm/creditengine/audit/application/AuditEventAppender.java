package com.srm.creditengine.audit.application;

import java.time.Instant;
import java.util.UUID;

/** Single append-only boundary that owns the shared audit journal row contract. */
public interface AuditEventAppender {
    void append(
            String actor,
            String action,
            String targetType,
            UUID targetId,
            Instant occurredAt,
            String safeMetadata);
}
