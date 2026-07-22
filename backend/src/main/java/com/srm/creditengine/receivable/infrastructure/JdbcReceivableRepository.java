package com.srm.creditengine.receivable.infrastructure;

import com.srm.creditengine.receivable.application.ReceivableRepository;
import com.srm.creditengine.receivable.domain.Receivable;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC-backed implementation of {@link ReceivableRepository}. */
@Repository
class JdbcReceivableRepository implements ReceivableRepository {

    private final JdbcTemplate jdbc;

    JdbcReceivableRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Receivable receivable) {
        jdbc.update(
                "insert into receivables"
                        + " (id,assignor_id,product_type_code,face_currency_code,face_amount,"
                        + "issue_date,due_date,status,version,created_at,created_by)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?)",
                receivable.id(),
                receivable.assignorId(),
                receivable.productType(),
                receivable.faceCurrency(),
                receivable.faceAmount(),
                Date.valueOf(receivable.issueDate()),
                Date.valueOf(receivable.dueDate()),
                receivable.status(),
                receivable.version(),
                Timestamp.from(receivable.createdAt()),
                receivable.createdBy());
    }

    @Override
    public Optional<Receivable> findById(UUID id) {
        return rows(" where id=?", id).stream().findFirst();
    }

    @Override
    public List<Receivable> findAll() {
        return rows(" order by created_at,id");
    }

    private List<Receivable> rows(String suffix, Object... args) {
        return jdbc.query(
                "select id,assignor_id,product_type_code,face_amount,face_currency_code,"
                        + "issue_date,due_date,status,version from receivables" + suffix,
                (rs, row) -> new Receivable(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        rs.getBigDecimal(4),
                        rs.getString(5),
                        rs.getDate(6).toLocalDate(),
                        rs.getDate(7).toLocalDate(),
                        rs.getString(8),
                        rs.getLong(9),
                        null,
                        null),
                args);
    }
}
