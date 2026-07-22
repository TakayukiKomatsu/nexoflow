package com.srm.creditengine.currency.infrastructure;

import com.srm.creditengine.currency.application.ReferenceRateRepository;
import com.srm.creditengine.currency.application.ReferenceRateService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
public class JdbcReferenceRateRepository implements ReferenceRateRepository, ReferenceRateService {
    private final JdbcTemplate jdbc;

    public JdbcReferenceRateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void recordBaseRate(String currency, BigDecimal monthlyRate, Instant effectiveAt) {
        requirePositive(monthlyRate);
        jdbc.update(
                "insert into base_rate_versions (id,currency_code,monthly_rate,effective_at) values (?,?,?,?)",
                UUID.randomUUID(), currency, monthlyRate, Timestamp.from(effectiveAt));
    }

    @Override
    public List<BaseRate> baseRates(String currency, Instant effectiveAt) {
        return jdbc.query(
                "select currency_code,monthly_rate,effective_at from base_rate_versions where currency_code=? and effective_at<=? order by effective_at desc",
                (rs, row) -> new BaseRate(rs.getString(1), rs.getBigDecimal(2), rs.getTimestamp(3).toInstant()),
                currency,
                Timestamp.from(effectiveAt));
    }

    @Override
    @Transactional
    public void recordProductSpread(String productType, BigDecimal monthlySpread, Instant effectiveAt) {
        requirePositive(monthlySpread);
        jdbc.update(
                "insert into product_spread_versions (id,product_type_code,monthly_spread,effective_at) values (?,?,?,?)",
                UUID.randomUUID(), productType, monthlySpread, Timestamp.from(effectiveAt));
    }

    @Override
    public List<ProductSpread> productSpreads(String productType, Instant effectiveAt) {
        return jdbc.query(
                "select product_type_code,monthly_spread,effective_at from product_spread_versions where product_type_code=? and effective_at<=? order by effective_at desc",
                (rs, row) -> new ProductSpread(rs.getString(1), rs.getBigDecimal(2), rs.getTimestamp(3).toInstant()),
                productType,
                Timestamp.from(effectiveAt));
    }

    private static void requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("A reference rate must be positive");
    }
}
