package com.srm.creditengine.currency.application;

import com.srm.creditengine.currency.FxConversionService;
import com.srm.creditengine.currency.SupportedCurrency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JdbcCurrencyService implements CurrencyService {
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final String OBSERVATIONS_SQL =
            "select base_currency_code,quote_currency_code,rate,source,observed_at "
                    + "from exchange_rates where base_currency_code=? and quote_currency_code=? "
                    + "order by observed_at desc";
    private static final String LATEST_SQL =
            "select base_currency_code,quote_currency_code,rate,source,observed_at "
                    + "from exchange_rates where base_currency_code=? and quote_currency_code=? "
                    + "and observed_at<=? order by observed_at desc limit 1";
    private static final RowMapper<Observation> OBSERVATION_MAPPER = (rs, row) -> new Observation(
            rs.getString(1),
            rs.getString(2),
            rs.getBigDecimal(3),
            rs.getString(4),
            rs.getTimestamp(5).toInstant());
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final FxConversionService conversion = new FxConversionService();
    JdbcCurrencyService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override @Transactional
    public void recordObservation(
            String base,
            String quote,
            BigDecimal rate,
            String source,
            Instant observedAt,
            String actor) {
        String canonicalBase = SupportedCurrency.require(base);
        String canonicalQuote = SupportedCurrency.require(quote);
        validateObservation(canonicalBase, canonicalQuote, rate, source, observedAt, actor);
        jdbc.update(
                "insert into exchange_rates "
                        + "(id,base_currency_code,quote_currency_code,rate,source,observed_at,created_at,created_by) "
                        + "values (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                canonicalBase,
                canonicalQuote,
                rate,
                source,
                Timestamp.from(observedAt),
                Timestamp.from(clock.instant()),
                actor);
    }

    private static void validateObservation(
            String base, String quote, BigDecimal rate, String source, Instant observedAt, String actor) {
        if (base.equals(quote)) {
            throw new IllegalArgumentException("Base and quote currencies must differ");
        }
        if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("A rate must be positive");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Rate source is required");
        if (observedAt == null) throw new IllegalArgumentException("Rate observation time is required");
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Actor is required");
    }
    @Override
    public List<Observation> observations(String base, String quote) {
        String canonicalBase = SupportedCurrency.require(base);
        String canonicalQuote = SupportedCurrency.require(quote);
        return jdbc.query(OBSERVATIONS_SQL, OBSERVATION_MAPPER, canonicalBase, canonicalQuote);
    }
    @Override
    public Conversion resolveConversion(String base, String quote, BigDecimal amount, Instant at) {
        String canonicalBase = SupportedCurrency.require(base);
        String canonicalQuote = SupportedCurrency.require(quote);
        if (canonicalBase.equals(canonicalQuote)) {
            BigDecimal raw = conversion.identity(amount);
            return new Conversion(
                    new Observation(canonicalBase, canonicalQuote, BigDecimal.ONE, "IDENTITY", at),
                    raw,
                    raw.setScale(2, RoundingMode.HALF_EVEN));
        }

        Observation direct = latest(canonicalBase, canonicalQuote, at);
        Observation inverse = latest(canonicalQuote, canonicalBase, at);
        Observation selected;
        boolean usesInverse;
        if (isFresh(direct, at)) {
            selected = direct;
            usesInverse = false;
        } else if (isFresh(inverse, at)) {
            selected = inverse;
            usesInverse = true;
        } else if (direct == null && inverse == null) {
            throw new FxRateMissingException();
        } else {
            throw new FxRateStaleException();
        }

        BigDecimal raw = usesInverse
                ? conversion.inverse(amount, selected.rate())
                : conversion.direct(amount, selected.rate());
        return new Conversion(selected, raw, raw.setScale(2, RoundingMode.HALF_EVEN));
    }
    private Observation latest(String base, String quote, Instant at) {
        List<Observation> values =
                jdbc.query(LATEST_SQL, OBSERVATION_MAPPER, base, quote, Timestamp.from(at));
        return values.isEmpty() ? null : values.getFirst();
    }

    private static boolean isFresh(Observation observation, Instant at) {
        return observation != null && !observation.observedAt().isBefore(at.minus(MAX_AGE));
    }
}
