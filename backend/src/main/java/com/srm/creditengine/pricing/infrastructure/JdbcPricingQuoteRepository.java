package com.srm.creditengine.pricing.infrastructure;

import com.srm.creditengine.pricing.application.PricingQuoteRepository;
import com.srm.creditengine.pricing.domain.PricingQuoteSnapshot;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPricingQuoteRepository implements PricingQuoteRepository {
    private final JdbcTemplate jdbc;

    public JdbcPricingQuoteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PricingQuoteSnapshot snapshot, String actor) {
        jdbc.update(
                """
                insert into pricing_quotes
                    (id, receivable_id, settlement_currency_code, face_amount, face_currency_code,
                     product_type_code, due_date, pricing_at, expires_at, base_rate, spread,
                     strategy_code, day_count_convention, term_in_months, discounted_amount,
                     fx_base_currency_code, fx_quote_currency_code, fx_rate, fx_source,
                     fx_observed_at, settlement_amount, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.id(),
                snapshot.receivableId(),
                snapshot.settlementCurrency(),
                snapshot.faceAmount(),
                snapshot.faceCurrency(),
                snapshot.productType(),
                Date.valueOf(snapshot.dueDate()),
                Timestamp.from(snapshot.pricedAt()),
                Timestamp.from(snapshot.expiresAt()),
                snapshot.baseRate(),
                snapshot.spread(),
                snapshot.strategyCode(),
                snapshot.dayCountConvention(),
                snapshot.termInMonths(),
                snapshot.discountedAmount(),
                snapshot.fxBaseCurrency(),
                snapshot.fxQuoteCurrency(),
                snapshot.fxRate(),
                snapshot.fxSource(),
                Timestamp.from(snapshot.fxObservedAt()),
                snapshot.settlementAmount(),
                actor);
    }

    @Override
    public Optional<PricingQuoteSnapshot> findById(UUID id) {
        return jdbc.query(
                        """
                        select id, receivable_id, product_type_code, due_date,
                               settlement_currency_code, face_amount, face_currency_code,
                               pricing_at, expires_at, base_rate, spread, strategy_code,
                               day_count_convention, term_in_months, discounted_amount,
                               fx_base_currency_code, fx_quote_currency_code, fx_rate,
                               fx_source, fx_observed_at, settlement_amount, created_by, status
                        from pricing_quotes where id = ?
                        """,
                        (rs, row) -> new PricingQuoteSnapshot(
                                rs.getObject("id", UUID.class),
                                rs.getObject("receivable_id", UUID.class),
                                rs.getString("product_type_code"),
                                rs.getDate("due_date").toLocalDate(),
                                rs.getString("settlement_currency_code"),
                                rs.getBigDecimal("face_amount"),
                                rs.getString("face_currency_code"),
                                rs.getTimestamp("pricing_at").toInstant(),
                                rs.getTimestamp("expires_at").toInstant(),
                                rs.getBigDecimal("base_rate"),
                                rs.getBigDecimal("spread"),
                                rs.getString("strategy_code"),
                                rs.getString("day_count_convention"),
                                rs.getBigDecimal("term_in_months"),
                                rs.getBigDecimal("discounted_amount"),
                                rs.getString("fx_base_currency_code"),
                                rs.getString("fx_quote_currency_code"),
                                rs.getBigDecimal("fx_rate"),
                                rs.getString("fx_source"),
                                rs.getTimestamp("fx_observed_at").toInstant(),
                                rs.getBigDecimal("settlement_amount"),
                                rs.getString("created_by"),
                                rs.getString("status")),
                        id)
                .stream()
                .findFirst();
    }
}
