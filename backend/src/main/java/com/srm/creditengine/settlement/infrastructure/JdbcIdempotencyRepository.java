package com.srm.creditengine.settlement.infrastructure;

import com.srm.creditengine.settlement.application.IdempotencyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {
    private final JdbcTemplate jdbc;

    public JdbcIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public IdempotencyRecord claim(String actor, String operation, String key, String requestHash, Instant createdAt) {
        jdbc.update("insert into idempotency_records (id,actor,operation,idempotency_key,request_hash,status,created_at) values (?,?,?,?,?,?,?) on conflict (actor,operation,idempotency_key) do nothing",
                UUID.randomUUID(), actor, operation, key, requestHash, "PROCESSING", Timestamp.from(createdAt));
        return jdbc.query("select id,request_hash,settlement_id,reversal_id,status from idempotency_records where actor=? and operation=? and idempotency_key=? for update",
                (rs, row) -> new IdempotencyRecord(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5)),
                actor, operation, key).stream().findFirst().orElseThrow(() -> new IllegalStateException("Idempotency claim was not persisted"));
    }

    @Override
    public void completeSettlement(UUID recordId, UUID settlementId, Instant completedAt) {
        jdbc.update("update idempotency_records set settlement_id=?, status='COMPLETED', completed_at=? where id=? and status='PROCESSING'",
                settlementId, Timestamp.from(completedAt), recordId);
    }

    @Override
    public void completeReversal(UUID recordId, UUID reversalId, Instant completedAt) {
        jdbc.update("update idempotency_records set reversal_id=?, status='COMPLETED', completed_at=? where id=? and status='PROCESSING'",
                reversalId, Timestamp.from(completedAt), recordId);
    }
}
