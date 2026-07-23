package com.srm.creditengine.currency.infrastructure;

import com.srm.creditengine.currency.application.ReferenceRateAuditRecorder;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReferenceRateAuditRecorder implements ReferenceRateAuditRecorder {
    private final JdbcTemplate jdbc;

    public JdbcReferenceRateAuditRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(
            String actor, String action, String targetType, UUID targetId, Instant occurredAt) {
        jdbc.update(
                "insert into audit_events (id,actor,action,target_type,target_id,occurred_at,correlation_id,safe_metadata) values (?,?,?,?,?,?,?,?::jsonb)",
                UUID.randomUUID(),
                actor,
                action,
                targetType,
                targetId,
                Timestamp.from(occurredAt),
                MDC.get("correlationId"),
                "{}");
    }
}
