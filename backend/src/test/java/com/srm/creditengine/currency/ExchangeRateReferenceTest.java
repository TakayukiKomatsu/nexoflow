package com.srm.creditengine.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import java.math.BigDecimal;
import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.domain.FxRateStaleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExchangeRateReferenceTest {
    @Autowired DataSource dataSource;
    @Autowired CurrencyService currency;

    @BeforeEach
    void clearRates() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("delete from exchange_rates");
        }
    }

    @Test
    void exchangeRatesUseBaseAndQuoteCurrencies() throws Exception {
        try (Connection connection = dataSource.getConnection();
             var result = connection.createStatement().executeQuery("select count(*) from exchange_rates")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(0);
        }
    }

    @Test
    void exchangeRateConstraintsRejectInvalidAndDuplicateObservations() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThatThrownBy(() -> insertRate(connection, UUID.randomUUID(), "USD", "BRL", "0.00"))
                    .isInstanceOf(Exception.class);

            Instant observedAt = Instant.parse("2030-01-15T11:00:00Z");
            insertRate(
                    connection,
                    UUID.fromString("00000000-0000-0000-0000-000000000201"),
                    "USD",
                    "BRL",
                    "5.20",
                    observedAt);
            assertThatThrownBy(() -> insertRate(
                            connection,
                            UUID.fromString("00000000-0000-0000-0000-000000000202"),
                            "USD",
                            "BRL",
                            "5.30",
                            observedAt))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void FX_004_selectsLatestRateAndAcceptsTheExactTwentyFourHourBoundary() throws Exception {
        Instant now = Instant.parse("2030-01-15T12:00:00Z");
        try (Connection connection = dataSource.getConnection()) {
            insertRate(connection, UUID.randomUUID(), "USD", "BRL", "5.10", now.minusSeconds(24 * 60 * 60));
            insertRate(connection, UUID.randomUUID(), "USD", "BRL", "5.20", now.minusSeconds(60 * 60));
        }

        var result = currency.resolveConversion("USD", "BRL", new BigDecimal("100.00"), now);

        assertThat(result.observation().rate()).isEqualByComparingTo("5.20");
        assertThat(result.unroundedConvertedAmount()).isEqualByComparingTo("520.0000000000");
        assertThat(result.settlementAmount()).isEqualByComparingTo("520.00");
    }

    @Test
    void rateOlderThanTwentyFourHoursIsNotSelected() throws Exception {
        Instant now = Instant.parse("2030-01-15T12:00:00Z");
        try (Connection connection = dataSource.getConnection()) {
            insertRate(connection, UUID.randomUUID(), "USD", "BRL", "5.20", now.minusSeconds(24 * 60 * 60 + 1));
        }
        assertThatThrownBy(() -> currency.resolveConversion(
                "USD", "BRL", new BigDecimal("100.00"), now))
                .isExactlyInstanceOf(FxRateStaleException.class);
    }

    @Test
    void recordedObservationIsDurableAndRetainsItsProviderSource() {
        Instant observedAt = Instant.parse("2030-01-15T11:00:00Z");
        currency.recordObservation("USD", "BRL", new BigDecimal("5.20"), "mock-provider", observedAt,
                "admin@srm.local");

        assertThat(currency.observations("USD", "BRL"))
                .singleElement()
                .satisfies(observation -> {
                    assertThat(observation.rate()).isEqualByComparingTo("5.20");
                    assertThat(observation.source()).isEqualTo("mock-provider");
                    assertThat(observation.observedAt()).isEqualTo(observedAt);
                });
    }

    private void insertRate(Connection connection, UUID id, String base, String quote, String rate)
            throws Exception {
        insertRate(connection, id, base, quote, rate, Instant.parse("2030-01-15T11:00:00Z"));
    }

    private void insertRate(
            Connection connection, UUID id, String base, String quote, String rate, Instant observedAt)
            throws Exception {
        try (var statement = connection.prepareStatement(
                "insert into exchange_rates "
                        + "(id,base_currency_code,quote_currency_code,rate,source,observed_at,created_at,created_by) "
                        + "values (?,?,?,?,?,?,?,?)")) {
            statement.setObject(1, id);
            statement.setString(2, base);
            statement.setString(3, quote);
            statement.setBigDecimal(4, new java.math.BigDecimal(rate));
            statement.setString(5, "MOCK");
            statement.setTimestamp(6, Timestamp.from(observedAt));
            statement.setTimestamp(7, Timestamp.from(observedAt));
            statement.setString(8, "admin@srm.local");
            statement.executeUpdate();
        }
    }
}
