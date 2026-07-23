package com.srm.creditengine.settlement.infrastructure;

import com.srm.creditengine.settlement.application.SettlementRepository;
import com.srm.creditengine.settlement.application.SettlementService;
import com.srm.creditengine.settlement.domain.AlreadyReversedException;
import com.srm.creditengine.settlement.domain.AlreadySettledException;
import com.srm.creditengine.settlement.domain.LockedQuote;
import com.srm.creditengine.settlement.domain.LockedSettlement;
import com.srm.creditengine.settlement.domain.SettlementDraft;
import com.srm.creditengine.shared.domain.DomainResourceNotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSettlementRepository implements SettlementRepository {
    private final JdbcTemplate jdbc;

    public JdbcSettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LockedQuote> findQuotes(List<UUID> orderedIds) {
        return queryQuotes(orderedIds, false);
    }

    @Override
    public List<LockedQuote> lockQuotes(List<UUID> orderedIds) {
        return queryQuotes(orderedIds, true);
    }

    private List<LockedQuote> queryQuotes(List<UUID> orderedIds, boolean lock) {
        String placeholders = String.join(",", java.util.Collections.nCopies(orderedIds.size(), "?"));
        String sql = "select q.id,q.receivable_id,q.settlement_currency_code,q.settlement_amount,q.expires_at,q.status,r.assignor_id,r.status,r.version,r.face_currency_code,r.product_type_code "
                + "from pricing_quotes q join receivables r on r.id=q.receivable_id where q.id in (" + placeholders + ") "
                + "order by q.id" + (lock ? " for update of q, r" : "");
        return jdbc.query(sql, (rs, row) -> new LockedQuote(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getBigDecimal(4),
                rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getObject(7, UUID.class), rs.getString(8),
                rs.getLong(9), rs.getString(10), rs.getString(11)), orderedIds.toArray());
    }

    @Override
    public void saveCompleted(SettlementDraft draft) {
        jdbc.update("insert into settlements (id,assignor_id,settlement_currency_code,total_amount,status,created_at,created_by) values (?,?,?,?,?,?,?)",
                draft.settlementId(), draft.assignorId(), draft.settlementCurrency(), draft.totalAmount(), "COMPLETED", Timestamp.from(draft.createdAt()), draft.actor());
    }

    @Override
    public void saveItem(SettlementDraft draft, LockedQuote quote, int position) {
        jdbc.update("insert into settlement_items (id,settlement_id,quote_id,receivable_id,item_position,settlement_amount,asset_currency_code,product_type_code) values (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), draft.settlementId(), quote.quoteId(), quote.receivableId(), position,
                quote.settlementAmount(), quote.assetCurrency(), quote.productType());
    }

    @Override
    public void consumeQuoteAndReceivable(LockedQuote quote) {
        int updatedQuote = jdbc.update("update pricing_quotes set status='CONSUMED' where id=? and status='ACTIVE'", quote.quoteId());
        int updatedReceivable = jdbc.update("update receivables set status='SETTLED', version=version+1 where id=? and status='REGISTERED' and version=?", quote.receivableId(), quote.receivableVersion());
        if (updatedQuote != 1 || updatedReceivable != 1) {
            throw new AlreadySettledException(quote.settlementCurrency());
        }
    }

    @Override
    public Optional<SettlementService.Result> findResult(UUID settlementId) {
        return jdbc.query("select settlement_currency_code,total_amount,status,created_at from settlements where id=?", (rs, row) -> new Object[] {
                    rs.getString(1), rs.getBigDecimal(2), rs.getString(3), rs.getTimestamp(4).toInstant() }, settlementId)
                .stream().findFirst().map(header -> new SettlementService.Result(
                        settlementId, (String) header[2], settlementItems(settlementId), (String) header[0],
                        (java.math.BigDecimal) header[1], (Instant) header[3], false));
    }

    @Override
    public LockedSettlement lockSettlement(UUID settlementId) {
        UUID lockedId = jdbc.query("select id from settlements where id=? for update", (rs, row) -> rs.getObject(1, UUID.class), settlementId)
                .stream().findFirst().orElseThrow(DomainResourceNotFoundException::new);
        if (jdbc.queryForObject("select count(*) from settlement_reversals where settlement_id=?", Integer.class, lockedId) > 0) {
            throw new AlreadyReversedException();
        }
        List<UUID> receivableIds = jdbc.query("select receivable_id from settlement_items where settlement_id=? order by item_position for update", (rs, row) -> rs.getObject(1, UUID.class), lockedId);
        if (receivableIds.isEmpty()) {
            throw new IllegalArgumentException("Settlement has no items");
        }
        return new LockedSettlement(lockedId, receivableIds);
    }

    @Override
    public SettlementService.Reversal reverse(LockedSettlement settlement, String reason, Instant at, String actor) {
        UUID reversalId = UUID.randomUUID();
        jdbc.update("insert into settlement_reversals (id,settlement_id,reason,reversed_at,reversed_by) values (?,?,?,?,?)",
                reversalId, settlement.settlementId(), reason, Timestamp.from(at), actor);
        for (UUID receivableId : settlement.receivableIds()) {
            if (jdbc.update("update receivables set status='REVERSED', version=version+1 where id=? and status='SETTLED'", receivableId) != 1) {
                throw new AlreadyReversedException();
            }
        }
        return new SettlementService.Reversal(reversalId, settlement.settlementId(), reason, at, false);
    }

    @Override
    public Optional<SettlementService.Reversal> findReversal(UUID reversalId) {
        return jdbc.query("select id,settlement_id,reason,reversed_at from settlement_reversals where id=?", (rs, row) -> new SettlementService.Reversal(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getTimestamp(4).toInstant(), false), reversalId)
                .stream().findFirst();
    }

    private List<SettlementService.Item> settlementItems(UUID settlementId) {
        return jdbc.query("select quote_id,receivable_id,settlement_amount from settlement_items where settlement_id=? order by item_position",
                (rs, row) -> new SettlementService.Item(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3)), settlementId);
    }
}
