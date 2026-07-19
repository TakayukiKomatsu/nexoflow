package com.srm.creditengine.assignor.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JdbcAssignorService implements AssignorService {
    private final JdbcTemplate jdbc; private final Clock clock;
    JdbcAssignorService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Override @Transactional public Assignor create(CreateCommand command) {
        String taxId = normalize(command.taxId());
        UUID id = command.id() == null ? UUID.randomUUID() : command.id(); Instant now = clock.instant();
        jdbc.update("insert into assignors (id,legal_name,normalized_tax_id,active,created_at,created_by) values (?,?,?,?,?,?)", id, command.legalName(), taxId, command.active(), Timestamp.from(now), command.actor());
        return new Assignor(id, command.legalName(), taxId, command.active(), now);
    }
    @Override public Assignor get(UUID id) { return jdbc.query("select id,legal_name,normalized_tax_id,active,created_at from assignors where id=?", (rs, row) -> new Assignor(rs.getObject(1, UUID.class),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getTimestamp(5).toInstant()), id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Assignor not found")); }
    @Override public List<Assignor> list() { return jdbc.query("select id,legal_name,normalized_tax_id,active,created_at from assignors order by created_at,id", (rs, row) -> new Assignor(rs.getObject(1, UUID.class),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getTimestamp(5).toInstant())); }
    private String normalize(String taxId) { String value = taxId.replaceAll("[^A-Za-z0-9]", "").toUpperCase(); if (value.isBlank()) throw new IllegalArgumentException("Tax ID is required"); return value; }
}
