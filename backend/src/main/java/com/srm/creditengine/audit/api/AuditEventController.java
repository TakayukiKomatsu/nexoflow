package com.srm.creditengine.audit.api;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Restricted inspection endpoint for append-only, redacted audit metadata. */
@RestController
class AuditEventController {
    private final JdbcTemplate jdbc;
    AuditEventController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/api/v1/audit-events")
    java.util.List<EventResponse> events(@RequestParam(defaultValue = "50") int size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("size is out of bounds");
        return jdbc.query("select id,actor,action,target_type,target_id,occurred_at,safe_metadata::text from audit_events order by occurred_at desc,id desc limit ?", (rs, row) -> new EventResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getTimestamp(6).toInstant(), rs.getString(7)), size);
    }
    record EventResponse(UUID id, String actor, String action, String targetType, UUID targetId, Instant occurredAt, String safeMetadata) {}
}
