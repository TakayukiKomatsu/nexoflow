package com.srm.creditengine.currency.infrastructure;

import com.srm.creditengine.currency.application.ReferenceRateRepository;
import com.srm.creditengine.currency.application.ReferenceRateService.BaseRate;
import com.srm.creditengine.currency.application.ReferenceRateService.ProductSpread;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcReferenceRateRepository implements ReferenceRateRepository {
    private final JdbcTemplate jdbc;

    public JdbcReferenceRateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordBaseRate(
            UUID id, String currency, BigDecimal monthlyRate, Instant effectiveAt, String actor) {
        jdbc.update(
                "insert into base_rate_versions (id,currency_code,monthly_rate,effective_at,created_by) values (?,?,?,?,?)",
                id, currency, monthlyRate, Timestamp.from(effectiveAt), actor);
    }

    @Override
    public List<BaseRate> baseRates(String currency, Instant effectiveAt) {
        return jdbc.query(
                "select currency_code,monthly_rate,effective_at,created_by from base_rate_versions where currency_code=? and effective_at<=? order by effective_at desc",
                (rs, row) -> new BaseRate(
                        rs.getString(1), rs.getBigDecimal(2), rs.getTimestamp(3).toInstant(), rs.getString(4)),
                currency,
                Timestamp.from(effectiveAt));
    }

    @Override
    public void recordProductSpread(
            UUID id, String productType, BigDecimal monthlySpread, Instant effectiveAt, String actor) {
        jdbc.update(
                "insert into product_spread_versions (id,product_type_code,monthly_spread,effective_at,created_by) values (?,?,?,?,?)",
                id, productType, monthlySpread, Timestamp.from(effectiveAt), actor);
    }

    @Override
    public List<ProductSpread> productSpreads(String productType, Instant effectiveAt) {
        return jdbc.query(
                "select product_type_code,monthly_spread,effective_at,created_by from product_spread_versions where product_type_code=? and effective_at<=? order by effective_at desc",
                (rs, row) -> new ProductSpread(
                        rs.getString(1), rs.getBigDecimal(2), rs.getTimestamp(3).toInstant(), rs.getString(4)),
                productType,
                Timestamp.from(effectiveAt));
    }
}
