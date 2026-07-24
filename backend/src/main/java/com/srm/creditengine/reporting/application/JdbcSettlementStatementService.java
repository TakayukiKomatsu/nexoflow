package com.srm.creditengine.reporting.application;

import com.srm.creditengine.currency.domain.ReferenceRatePolicy;
import com.srm.creditengine.currency.domain.SupportedCurrency;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;

@Service
class JdbcSettlementStatementService implements SettlementStatementService {
    /** Offset pagination is bounded to a predictable read window; deeper reads require a future cursor contract. */
    static final long MAX_OFFSET = 10_000L;
    private static final SettlementStatementSql STATEMENT_SQL = SettlementStatementSql.fromClasspath();
    private final JdbcTemplate jdbc;
    private final FinancialTelemetry telemetry;
    JdbcSettlementStatementService(JdbcTemplate jdbc, FinancialTelemetry telemetry) { this.jdbc = jdbc; this.telemetry = telemetry; }

    @Override public Page query(Filter filter) {
        var timing = telemetry.startReport();
        try {
            return queryUnchecked(filter);
        } catch (RuntimeException exception) {
            telemetry.report("rejected");
            throw exception;
        } finally {
            telemetry.completeReport(timing);
        }
    }

    private Page queryUnchecked(Filter filter) {
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) throw new IllegalArgumentException("from must be before to");
        if (filter.page() < 0 || filter.size() < 1 || filter.size() > 100) throw new IllegalArgumentException("page and size are out of bounds");
        Filter canonicalFilter = new Filter(
                filter.from(),
                filter.to(),
                filter.assignorId(),
                optionalCurrency(filter.assetCurrency()),
                optionalCurrency(filter.settlementCurrency()),
                optionalProductType(filter.productType()),
                filter.page(),
                filter.size());
        long offset;
        try {
            offset = Math.multiplyExact((long) canonicalFilter.page(), (long) canonicalFilter.size());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page offset is out of bounds", exception);
        }
        if (offset > MAX_OFFSET) throw new IllegalArgumentException("page offset is out of bounds");
        var query = STATEMENT_SQL.render(canonicalFilter, canonicalFilter.size() + 1, offset);
        var rows = jdbc.query(query.sql(), (rs, row) -> new Entry(rs.getObject(1, java.util.UUID.class), rs.getString(2), rs.getBigDecimal(3), rs.getTimestamp(4).toInstant(), rs.getObject(5, java.util.UUID.class), rs.getObject(6, java.util.UUID.class), rs.getObject(7, java.util.UUID.class), rs.getString(8), rs.getString(9), rs.getString(10), rs.getObject(11, java.util.UUID.class)), query.arguments().toArray());
        boolean hasNext = rows.size() > canonicalFilter.size(); if (hasNext) rows = rows.subList(0, canonicalFilter.size());
        telemetry.report("success");
        return new Page(List.copyOf(rows), canonicalFilter.page(), canonicalFilter.size(), hasNext);
    }

    private static String optionalCurrency(String value) {
        return value == null ? null : SupportedCurrency.require(value);
    }

    private static String optionalProductType(String value) {
        return value == null ? null : ReferenceRatePolicy.requireProductType(value);
    }
}
