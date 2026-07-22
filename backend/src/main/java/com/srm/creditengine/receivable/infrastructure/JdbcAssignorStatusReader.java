package com.srm.creditengine.receivable.infrastructure;

import com.srm.creditengine.receivable.application.AssignorStatusReader;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC-backed implementation of {@link AssignorStatusReader}. */
@Repository
class JdbcAssignorStatusReader implements AssignorStatusReader {

    private final JdbcTemplate jdbc;

    JdbcAssignorStatusReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActive(UUID assignorId) {
        return jdbc.query(
                        "select active from assignors where id=?",
                        (rs, row) -> rs.getBoolean(1),
                        assignorId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Assignor not found"));
    }
}
