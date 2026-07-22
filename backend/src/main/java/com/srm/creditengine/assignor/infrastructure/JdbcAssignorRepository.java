package com.srm.creditengine.assignor.infrastructure;

import com.srm.creditengine.assignor.application.AssignorRepository;
import com.srm.creditengine.assignor.domain.Assignor;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC-backed implementation of {@link AssignorRepository}. */
@Repository
class JdbcAssignorRepository implements AssignorRepository {

    private final JdbcTemplate jdbc;

    JdbcAssignorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Assignor assignor) {
        jdbc.update(
                "insert into assignors"
                        + " (id,legal_name,normalized_tax_id,active,created_at,created_by)"
                        + " values (?,?,?,?,?,?)",
                assignor.id(),
                assignor.legalName(),
                assignor.taxId(),
                assignor.active(),
                Timestamp.from(assignor.createdAt()),
                assignor.createdBy());
    }

    @Override
    public Optional<Assignor> findById(UUID id) {
        return jdbc.query(
                        "select id,legal_name,normalized_tax_id,active,created_at"
                                + " from assignors where id=?",
                        (rs, row) -> new Assignor(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getBoolean(4),
                                rs.getTimestamp(5).toInstant(),
                                null),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public List<Assignor> findAll() {
        return jdbc.query(
                "select id,legal_name,normalized_tax_id,active,created_at"
                        + " from assignors order by created_at,id",
                (rs, row) -> new Assignor(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getBoolean(4),
                        rs.getTimestamp(5).toInstant(),
                        null));
    }
}
