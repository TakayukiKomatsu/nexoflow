package com.srm.creditengine.currency.infrastructure;

import com.srm.creditengine.currency.application.ExchangeRateRepository;
import com.srm.creditengine.currency.domain.FxObservation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExchangeRateRepository implements ExchangeRateRepository {
    private static final String OBSERVATIONS_SQL =
            "select base_currency_code,quote_currency_code,rate,source,observed_at "
                    + "from exchange_rates where base_currency_code=? and quote_currency_code=? "
                    + "order by observed_at desc";
    private static final String LATEST_SQL =
            "select base_currency_code,quote_currency_code,rate,source,observed_at "
                    + "from exchange_rates where base_currency_code=? and quote_currency_code=? "
                    + "and observed_at<=? order by observed_at desc,created_at desc,id desc limit 1";
    private static final RowMapper<FxObservation> OBSERVATION_MAPPER = (rs, row) -> new FxObservation(
            rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4), rs.getTimestamp(5).toInstant());

    private final JdbcTemplate jdbc;

    public JdbcExchangeRateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID id, FxObservation observation, String actor, Instant createdAt) {
        jdbc.update(
                "insert into exchange_rates "
                        + "(id,base_currency_code,quote_currency_code,rate,source,observed_at,created_at,created_by) "
                        + "values (?,?,?,?,?,?,?,?)",
                id,
                observation.base(),
                observation.quote(),
                observation.rate(),
                observation.source(),
                Timestamp.from(observation.observedAt()),
                Timestamp.from(createdAt),
                actor);
    }

    @Override
    public Optional<FxObservation> latest(String base, String quote, Instant at) {
        return jdbc.query(LATEST_SQL, OBSERVATION_MAPPER, base, quote, Timestamp.from(at)).stream().findFirst();
    }

    @Override
    public List<FxObservation> observations(String base, String quote) {
        return jdbc.query(OBSERVATIONS_SQL, OBSERVATION_MAPPER, base, quote);
    }
}
