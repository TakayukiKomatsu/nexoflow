package com.srm.creditengine.audit.infrastructure;

import com.srm.creditengine.audit.application.AuditEventQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditEventQuery implements AuditEventQuery {
    private final JdbcTemplate jdbc;

    public JdbcAuditEventQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Event> latest(int size) {
        return jdbc.query(
                "select id,actor,action,target_type,target_id,occurred_at,correlation_id,safe_metadata::text "
                        + "from audit_events order by occurred_at desc,id desc limit ?",
                (rs, row) -> new Event(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getObject(5, UUID.class),
                        rs.getTimestamp(6).toInstant(),
                        rs.getString(7),
                        rs.getString(8)),
                size);
    }
}
