package com.srm.creditengine.receivable.application;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JdbcReceivableService implements ReceivableService {
    private final JdbcTemplate jdbc; private final Clock clock;
    JdbcReceivableService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Override @Transactional public Receivable register(RegisterCommand c) {
        if (c.faceAmount() == null || c.faceAmount().signum() <= 0 || c.faceAmount().scale() > 4 || c.issueDate() == null || c.dueDate() == null || !c.dueDate().isAfter(c.issueDate())) throw new IllegalArgumentException("Receivable face amount must be positive with no more than four decimal places and due date must be after issue date");
        Boolean assignorActive = jdbc.query("select active from assignors where id=?", (rs, row) -> rs.getBoolean(1), c.assignorId()).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Assignor not found"));
        if (!assignorActive) throw new IllegalArgumentException("Receivable requires an active assignor");
        UUID id = c.id() == null ? UUID.randomUUID() : c.id();
        jdbc.update("insert into receivables (id,assignor_id,product_type_code,face_currency_code,face_amount,issue_date,due_date,status,version,created_at,created_by) values (?,?,?,?,?,?,?,?,?,?,?)", id,c.assignorId(),c.productType(),c.faceCurrency(),c.faceAmount(),Date.valueOf(c.issueDate()),Date.valueOf(c.dueDate()),"REGISTERED",0,Timestamp.from(clock.instant()),c.actor());
        return new Receivable(id,c.assignorId(),c.productType(),c.faceAmount(),c.faceCurrency(),c.issueDate(),c.dueDate(),"REGISTERED",0);
    }
    @Override public Receivable get(UUID id) { return rows(" where id=?", id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Receivable not found")); }
    @Override public List<Receivable> list() { return rows(" order by created_at,id"); }
    private List<Receivable> rows(String suffix, Object... args) { return jdbc.query("select id,assignor_id,product_type_code,face_amount,face_currency_code,issue_date,due_date,status,version from receivables" + suffix, (rs,row) -> new Receivable(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getBigDecimal(4),rs.getString(5),rs.getDate(6).toLocalDate(),rs.getDate(7).toLocalDate(),rs.getString(8),rs.getLong(9)),args); }
}
