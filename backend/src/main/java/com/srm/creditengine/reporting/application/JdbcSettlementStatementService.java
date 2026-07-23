package com.srm.creditengine.reporting.application;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;

@Service
class JdbcSettlementStatementService implements SettlementStatementService {
    /** Offset pagination is bounded to a predictable read window; deeper reads require a future cursor contract. */
    static final long MAX_OFFSET = 10_000L;
    private final JdbcTemplate jdbc;
    private final FinancialTelemetry telemetry;
    JdbcSettlementStatementService(JdbcTemplate jdbc, FinancialTelemetry telemetry) { this.jdbc = jdbc; this.telemetry = telemetry; }

    @Override public Page query(Filter filter) {
        try {
            return queryUnchecked(filter);
        } catch (RuntimeException exception) {
            telemetry.report("rejected");
            throw exception;
        }
    }

    private Page queryUnchecked(Filter filter) {
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) throw new IllegalArgumentException("from must be before to");
        if (filter.page() < 0 || filter.size() < 1 || filter.size() > 100) throw new IllegalArgumentException("page and size are out of bounds");
        long offset;
        try {
            offset = Math.multiplyExact((long) filter.page(), (long) filter.size());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page offset is out of bounds", exception);
        }
        if (offset > MAX_OFFSET) throw new IllegalArgumentException("page offset is out of bounds");
        List<Object> args = new ArrayList<>(); String where = where(filter, args);
        String sql = "select entry_id,entry_type,signed_amount,effective_at,settlement_id,reversal_id,assignor_id,asset_currency_code,settlement_currency_code,product_type_code,receivable_id from ("
                + "select md5('SETTLEMENT:' || i.id::text)::uuid entry_id,'SETTLEMENT' entry_type,i.settlement_amount signed_amount,s.created_at effective_at,s.id settlement_id,null::uuid reversal_id,s.assignor_id,i.asset_currency_code,s.settlement_currency_code,i.product_type_code,i.receivable_id from settlements s join settlement_items i on i.settlement_id=s.id "
                + "union all select md5('REVERSAL:' || r.id::text || ':' || i.id::text)::uuid entry_id,'REVERSAL' entry_type,-i.settlement_amount signed_amount,r.reversed_at effective_at,s.id settlement_id,r.id reversal_id,s.assignor_id,i.asset_currency_code,s.settlement_currency_code,i.product_type_code,i.receivable_id from settlement_reversals r join settlements s on s.id=r.settlement_id join settlement_items i on i.settlement_id=s.id"
                + ") ledger" + where + " order by effective_at desc, entry_id desc limit ? offset ?";
        args.add(filter.size() + 1); args.add(offset);
        var rows = jdbc.query(sql, (rs, row) -> new Entry(rs.getObject(1, java.util.UUID.class), rs.getString(2), rs.getBigDecimal(3), rs.getTimestamp(4).toInstant(), rs.getObject(5, java.util.UUID.class), rs.getObject(6, java.util.UUID.class), rs.getObject(7, java.util.UUID.class), rs.getString(8), rs.getString(9), rs.getString(10), rs.getObject(11, java.util.UUID.class)), args.toArray());
        boolean hasNext = rows.size() > filter.size(); if (hasNext) rows = rows.subList(0, filter.size());
        telemetry.report("success");
        return new Page(List.copyOf(rows), filter.page(), filter.size(), hasNext);
    }
    private String where(Filter filter, List<Object> args) {
        List<String> parts = new ArrayList<>();
        if (filter.from() != null) { parts.add("effective_at >= ?"); args.add(Timestamp.from(filter.from())); }
        if (filter.to() != null) { parts.add("effective_at < ?"); args.add(Timestamp.from(filter.to())); }
        if (filter.assignorId() != null) { parts.add("assignor_id = ?"); args.add(filter.assignorId()); }
        if (filter.assetCurrency() != null) { parts.add("asset_currency_code = ?"); args.add(filter.assetCurrency()); }
        if (filter.settlementCurrency() != null) { parts.add("settlement_currency_code = ?"); args.add(filter.settlementCurrency()); }
        if (filter.productType() != null) { parts.add("product_type_code = ?"); args.add(filter.productType()); }
        return parts.isEmpty() ? "" : " where " + String.join(" and ", parts);
    }
}
