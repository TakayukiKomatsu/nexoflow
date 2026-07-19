package com.srm.creditengine.settlement.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;

@Service
class JdbcSettlementService implements SettlementService {
    private static final String OPERATION = "SETTLEMENT_CREATE";
    private static final String REVERSAL_OPERATION = "SETTLEMENT_REVERSE";
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final FinancialTelemetry telemetry;

    JdbcSettlementService(JdbcTemplate jdbc, Clock clock, FinancialTelemetry telemetry) { this.jdbc = jdbc; this.clock = clock; this.telemetry = telemetry; }

    @Override
    public Preview preview(List<UUID> orderedQuoteIds, String actor) {
        var quotes = validate(orderedQuoteIds, false);
        return previewOf(quotes, clock.instant());
    }

    @Override
    @Transactional
    public Result settle(List<UUID> orderedQuoteIds, String idempotencyKey, String actor) {
        requireOrderedUnique(orderedQuoteIds);
        String hash = hash(orderedQuoteIds);
        claim(actor, idempotencyKey, hash);
        var existing = idempotency(actor, idempotencyKey);
        if (!existing.hash.equals(hash)) throw new IdempotencyKeyReusedException();
        if ("COMPLETED".equals(existing.status)) return result(existing.settlementId, true);

        var quotes = validate(orderedQuoteIds, true);
        Instant now = clock.instant();
        Preview preview = previewOf(quotes, now);
        UUID settlementId = UUID.randomUUID();
        UUID assignorId = quotes.getFirst().assignorId;
        jdbc.update("insert into settlements (id,assignor_id,settlement_currency_code,total_amount,status,created_at,created_by) values (?,?,?,?,?,?,?)",
                settlementId, assignorId, preview.settlementCurrency(), preview.totalAmount(), "COMPLETED", Timestamp.from(now), actor);
        for (int index = 0; index < quotes.size(); index++) {
            Quote quote = quotes.get(index);
            int updatedQuote = jdbc.update("update pricing_quotes set status='CONSUMED' where id=? and status='ACTIVE'", quote.id);
            int updatedReceivable = jdbc.update("update receivables set status='SETTLED', version=version+1 where id=? and status='REGISTERED' and version=?", quote.receivableId, quote.receivableVersion);
            if (updatedQuote != 1 || updatedReceivable != 1) throw new AlreadySettledException();
            jdbc.update("insert into settlement_items (id,settlement_id,quote_id,receivable_id,item_position,settlement_amount,asset_currency_code,product_type_code) values (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), settlementId, quote.id, quote.receivableId, index + 1, quote.amount, quote.assetCurrency, quote.productType);
        }
        jdbc.update("update idempotency_records set settlement_id=?, status='COMPLETED', completed_at=? where id=? and status='PROCESSING'",
                settlementId, Timestamp.from(now), existing.id);
        audit(actor, "SETTLEMENT_CREATED", "SETTLEMENT", settlementId, now, "{\"itemCount\":" + quotes.size() + "}");
        telemetry.settlement(preview.settlementCurrency(), "success");
        return result(settlementId, false);
    }

    @Override
    public Result get(UUID settlementId) {
        if (settlementId == null) throw new IllegalArgumentException("Settlement ID is required");
        return result(settlementId, false);
    }

    @Override
    @Transactional
    public Reversal reverse(UUID settlementId, String reason, String idempotencyKey, String actor) {
        if (settlementId == null || reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("A reversal reason is required");
        String hash = reversalHash(settlementId, reason.trim());
        claim(actor, REVERSAL_OPERATION, idempotencyKey, hash);
        var existing = reversalIdempotency(actor, idempotencyKey);
        if (!existing.hash.equals(hash)) throw new IdempotencyKeyReusedException();
        if ("COMPLETED".equals(existing.status)) return reversal(existing.reversalId, true);
        var settlement = jdbc.query("select id from settlements where id=? for update", (rs, row) -> rs.getObject(1, UUID.class), settlementId).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Settlement not found"));
        if (jdbc.queryForObject("select count(*) from settlement_reversals where settlement_id=?", Integer.class, settlement) > 0) throw new AlreadyReversedException();
        var itemIds = jdbc.query("select receivable_id from settlement_items where settlement_id=? order by item_position for update", (rs, row) -> rs.getObject(1, UUID.class), settlement);
        if (itemIds.isEmpty()) throw new IllegalArgumentException("Settlement has no items");
        Instant now = clock.instant(); UUID reversalId = UUID.randomUUID();
        jdbc.update("insert into settlement_reversals (id,settlement_id,reason,reversed_at,reversed_by) values (?,?,?,?,?)", reversalId, settlement, reason.trim(), Timestamp.from(now), actor);
        for (UUID receivableId : itemIds) {
            if (jdbc.update("update receivables set status='REVERSED', version=version+1 where id=? and status='SETTLED'", receivableId) != 1) throw new AlreadyReversedException();
        }
        jdbc.update("update idempotency_records set reversal_id=?, status='COMPLETED', completed_at=? where id=? and status='PROCESSING'", reversalId, Timestamp.from(now), existing.id);
        audit(actor, "SETTLEMENT_REVERSED", "SETTLEMENT_REVERSAL", reversalId, now, "{\"settlementId\":\"" + settlement + "\"}");
        telemetry.reversal("success");
        return new Reversal(reversalId, settlement, reason.trim(), now, false);
    }

    private void claim(String actor, String key, String hash) { claim(actor, OPERATION, key, hash); }
    private void claim(String actor, String operation, String key, String hash) {
        jdbc.update("insert into idempotency_records (id,actor,operation,idempotency_key,request_hash,status,created_at) values (?,?,?,?,?,?,?) on conflict (actor,operation,idempotency_key) do nothing",
                UUID.randomUUID(), actor, operation, key, hash, "PROCESSING", Timestamp.from(clock.instant()));
    }

    private ReversalIdempotency reversalIdempotency(String actor, String key) {
        return jdbc.query("select id,request_hash,reversal_id,status from idempotency_records where actor=? and operation=? and idempotency_key=? for update",
                (rs, row) -> new ReversalIdempotency(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4)), actor, REVERSAL_OPERATION, key).stream().findFirst().orElseThrow();
    }
    private Reversal reversal(UUID reversalId, boolean replayed) {
        return jdbc.query("select id,settlement_id,reason,reversed_at from settlement_reversals where id=?", (rs, row) -> new Reversal(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getTimestamp(4).toInstant(), replayed), reversalId).stream().findFirst().orElseThrow();
    }
    private void audit(String actor, String action, String type, UUID target, Instant at, String metadata) {
        jdbc.update("insert into audit_events (id,actor,action,target_type,target_id,occurred_at,correlation_id,safe_metadata) values (?,?,?,?,?,?,?,?::jsonb)",
                UUID.randomUUID(), actor, action, type, target, Timestamp.from(at), MDC.get("correlationId"), metadata);
    }

    private Idempotency idempotency(String actor, String key) {
        return jdbc.query("select id,request_hash,settlement_id,status from idempotency_records where actor=? and operation=? and idempotency_key=? for update",
                (rs, row) -> new Idempotency(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4)), actor, OPERATION, key)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Idempotency claim was not persisted"));
    }

    private List<Quote> validate(List<UUID> ids, boolean lock) {
        requireOrderedUnique(ids);
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "select q.id,q.receivable_id,q.settlement_currency_code,q.settlement_amount,q.expires_at,q.status,r.assignor_id,r.status,r.version,r.face_currency_code,r.product_type_code "
                + "from pricing_quotes q join receivables r on r.id=q.receivable_id where q.id in (" + placeholders + ")" + (lock ? " for update of q, r" : "");
        var found = jdbc.query(sql, (rs, row) -> new Quote(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getBigDecimal(4), rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getObject(7, UUID.class), rs.getString(8), rs.getLong(9), rs.getString(10), rs.getString(11)), ids.toArray());
        if (found.size() != ids.size()) throw new IllegalArgumentException("One or more pricing quotes were not found");
        var byId = new HashMap<UUID, Quote>();
        found.forEach(q -> byId.put(q.id, q));
        List<Quote> ordered = new ArrayList<>();
        Instant now = clock.instant();
        for (UUID id : ids) {
            Quote q = byId.get(id);
            if ("CONSUMED".equals(q.quoteStatus)) throw new AlreadySettledException();
            if (!"ACTIVE".equals(q.quoteStatus) || !now.isBefore(q.expiresAt)) throw new PricingQuoteExpiredException();
            if (!"REGISTERED".equals(q.receivableStatus)) throw new AlreadySettledException();
            ordered.add(q);
        }
        Quote first = ordered.getFirst();
        if (ordered.stream().anyMatch(q -> !q.assignorId.equals(first.assignorId) || !q.currency.equals(first.currency)))
            throw new IllegalArgumentException("Pricing quotes must have one assignor and settlement currency");
        return ordered;
    }

    private Preview previewOf(List<Quote> quotes, Instant asOf) {
        BigDecimal total = quotes.stream().map(q -> q.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant earliest = quotes.stream().map(q -> q.expiresAt).min(Instant::compareTo).orElseThrow();
        return new Preview(quotes.stream().map(q -> new Item(q.id, q.receivableId, q.amount)).toList(), quotes.getFirst().currency, total, asOf, earliest);
    }

    private Result result(UUID settlementId, boolean replayed) {
        var header = jdbc.query("select settlement_currency_code,total_amount,status,created_at from settlements where id=?", (rs, row) -> new Object[] { rs.getString(1), rs.getBigDecimal(2), rs.getString(3), rs.getTimestamp(4).toInstant() }, settlementId).stream().findFirst().orElseThrow();
        var items = jdbc.query("select quote_id,receivable_id,settlement_amount from settlement_items where settlement_id=? order by item_position", (rs, row) -> new Item(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3)), settlementId);
        return new Result(settlementId, (String) header[2], items, (String) header[0], (BigDecimal) header[1], (Instant) header[3], replayed);
    }

    private void requireOrderedUnique(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("At least one pricing quote is required");
        if (ids.stream().anyMatch(java.util.Objects::isNull) || ids.stream().distinct().count() != ids.size()) throw new IllegalArgumentException("Pricing quote IDs must be ordered and unique");
    }

    private String hash(List<UUID> ids) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(String.join(",", ids.stream().map(UUID::toString).toList()).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private String reversalHash(UUID settlementId, String reason) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((settlementId + "|" + reason).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private record Quote(UUID id, UUID receivableId, String currency, BigDecimal amount, Instant expiresAt, String quoteStatus, UUID assignorId, String receivableStatus, long receivableVersion, String assetCurrency, String productType) {}
    private record Idempotency(UUID id, String hash, UUID settlementId, String status) {}
    private record ReversalIdempotency(UUID id, String hash, UUID reversalId, String status) {}
}
