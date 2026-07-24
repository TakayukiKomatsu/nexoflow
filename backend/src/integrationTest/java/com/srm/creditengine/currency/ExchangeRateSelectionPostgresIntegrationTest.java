package com.srm.creditengine.currency;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.currency.application.CurrencyService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class ExchangeRateSelectionPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("srm_credit_engine")
            .withUsername("srm")
            .withPassword("srm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("srm.jwt-secret", () -> "srm-test-secret-do-not-use-32-bytes-minimum");
    }

    @Autowired CurrencyService currency;
    @Autowired JdbcTemplate jdbc;

    @Test
    void latestRateUsesTheMostRecentlyRecordedSourceWhenObservationTimesTie() {
        Instant observedAt = Instant.parse("2030-01-15T11:59:00Z");
        insertRate(
                UUID.randomUUID(),
                new BigDecimal("5.1000000000"),
                "earlier-recorded-source",
                observedAt,
                Instant.parse("2030-01-15T12:00:00Z"));
        insertRate(
                UUID.randomUUID(),
                new BigDecimal("5.2000000000"),
                "later-recorded-source",
                observedAt,
                Instant.parse("2030-01-15T12:01:00Z"));

        var selected = currency.resolveConversion(
                "USD", "BRL", new BigDecimal("100.00"), Instant.parse("2030-01-15T12:02:00Z"));

        assertThat(selected.observation())
                .extracting(CurrencyService.Observation::source, CurrencyService.Observation::rate)
                .containsExactly("later-recorded-source", new BigDecimal("5.2000000000"));
    }

    private void insertRate(UUID id, BigDecimal rate, String source, Instant observedAt, Instant createdAt) {
        jdbc.update(
                """
                insert into exchange_rates
                    (id, base_currency_code, quote_currency_code, rate, source,
                     observed_at, created_at, created_by)
                values (?, 'USD', 'BRL', ?, ?, ?, ?, 'selection-integration-test')
                """,
                id,
                rate,
                source,
                Timestamp.from(observedAt),
                Timestamp.from(createdAt));
    }
}
