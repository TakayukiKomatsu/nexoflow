package com.srm.creditengine.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
class ExchangeRateImmutabilityIntegrationTest {
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

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void exchangeRateHistoryRejectsUpdateAndDeleteAndPreservesOriginalRow() {
        UUID id = UUID.randomUUID();
        BigDecimal originalRate = new BigDecimal("5.1234567890");
        String source = "immutability-" + id.toString().substring(0, 8);
        Timestamp observedAt = Timestamp.valueOf(LocalDateTime.of(2026, 7, 18, 12, 0));
        Timestamp createdAt = Timestamp.valueOf(LocalDateTime.of(2026, 7, 18, 12, 1));

        jdbc.update("""
                        insert into exchange_rates
                            (id, base_currency_code, quote_currency_code, rate, source, observed_at, created_at, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, "USD", "BRL", originalRate, source, observedAt, createdAt, "immutability-integration-test");

        assertThatThrownBy(() -> jdbc.update("update exchange_rates set rate = 9.99 where id = ?", id))
                .hasMessageContaining("exchange_rates rows are immutable");
        assertThatThrownBy(() -> jdbc.update("delete from exchange_rates where id = ?", id))
                .hasMessageContaining("exchange_rates rows are immutable");

        ExchangeRateRow persisted = jdbc.queryForObject("""
                        select id, base_currency_code, quote_currency_code, rate, source, observed_at, created_at, created_by
                        from exchange_rates
                        where id = ?
                        """,
                (resultSet, rowNumber) -> new ExchangeRateRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("base_currency_code"),
                        resultSet.getString("quote_currency_code"),
                        resultSet.getBigDecimal("rate"),
                        resultSet.getString("source"),
                        resultSet.getTimestamp("observed_at"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getString("created_by")),
                id);

        assertThat(persisted).isEqualTo(new ExchangeRateRow(
                id,
                "USD",
                "BRL",
                originalRate,
                source,
                observedAt,
                createdAt,
                "immutability-integration-test"));
        assertThat(jdbc.queryForObject("select count(*) from exchange_rates where id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    private record ExchangeRateRow(
            UUID id,
            String baseCurrencyCode,
            String quoteCurrencyCode,
            BigDecimal rate,
            String source,
            Timestamp observedAt,
            Timestamp createdAt,
            String createdBy) {}
}
