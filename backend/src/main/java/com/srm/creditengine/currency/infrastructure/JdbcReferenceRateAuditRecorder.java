package com.srm.creditengine.currency.infrastructure;

import com.srm.creditengine.audit.application.AuditEventAppender;
import com.srm.creditengine.currency.application.ReferenceRateAuditRecorder;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReferenceRateAuditRecorder implements ReferenceRateAuditRecorder {
    private final AuditEventAppender auditEvents;

    public JdbcReferenceRateAuditRecorder(AuditEventAppender auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Override
    public void record(
            String actor, String action, String targetType, UUID targetId, Instant occurredAt) {
        auditEvents.append(
                actor,
                action,
                targetType,
                targetId,
                occurredAt,
                "{}");
    }
}
