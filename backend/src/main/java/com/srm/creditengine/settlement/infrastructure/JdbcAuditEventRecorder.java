package com.srm.creditengine.settlement.infrastructure;

import com.srm.creditengine.audit.application.AuditEventAppender;
import com.srm.creditengine.settlement.application.AuditEventRecorder;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditEventRecorder implements AuditEventRecorder {
    private final AuditEventAppender auditEvents;

    public JdbcAuditEventRecorder(AuditEventAppender auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Override
    public void record(String actor, String action, String targetType, UUID targetId, Instant occurredAt, String safeMetadata) {
        auditEvents.append(actor, action, targetType, targetId, occurredAt, safeMetadata);
    }
}
