package com.srm.creditengine.audit.infrastructure;

import com.srm.creditengine.audit.application.AuditEventAppender;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter that centralizes audit IDs, correlation, time conversion, and SQL. */
@Repository
public class JdbcAuditEventStore implements AuditEventAppender {
    private static final String INSERT_SQL = "insert into audit_events "
            + "(id,actor,action,target_type,target_id,occurred_at,correlation_id,safe_metadata) "
            + "values (?,?,?,?,?,?,?,?::jsonb)";

    private final JdbcTemplate jdbc;

    public JdbcAuditEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(
            String actor,
            String action,
            String targetType,
            UUID targetId,
            Instant occurredAt,
            String safeMetadata) {
        jdbc.update(
                INSERT_SQL,
                UUID.randomUUID(),
                actor,
                action,
                targetType,
                targetId,
                Timestamp.from(occurredAt),
                MDC.get("correlationId"),
                safeMetadata);
    }
}
