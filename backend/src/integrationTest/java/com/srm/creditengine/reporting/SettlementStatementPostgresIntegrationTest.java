package com.srm.creditengine.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.currency.domain.UnsupportedCurrencyException;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.receivable.application.ReceivableService;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import com.srm.creditengine.settlement.application.SettlementService;
import java.math.BigDecimal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
class SettlementStatementPostgresIntegrationTest {
    private static final Instant EFFECTIVE_AT = Instant.parse("2030-01-15T12:00:00Z");

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

    @Autowired SettlementStatementService statements;
    @Autowired SettlementService settlements;
    @Autowired PricingService pricing;
    @Autowired AssignorService assignors;
    @Autowired ReceivableService receivables;
    @Autowired DataSource dataSource;
    @Autowired FinancialTelemetry telemetry;

    @Test
    void reversedTwoItemSettlementHasStableDistinctFilterableSingleQueryMovements() {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(
                assignorId,
                "Statement Contract Co",
                "STMT" + assignorId.toString().substring(0, 8),
                true,
                "operator@srm.local"));
        List<UUID> receivableIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> quoteIds = receivableIds.stream()
                .map(receivableId -> createQuote(assignorId, receivableId))
                .toList();
        var settlement = settlements.settle(
                quoteIds, "statement-settle-" + assignorId, "operator@srm.local");
        settlements.reverse(
                settlement.settlementId(),
                "duplicate source document",
                "statement-reverse-" + assignorId,
                "operator@srm.local");

        var allFilter = filter(null, null, null, null, null, null, 0, 100);
        var preparedStatements = new AtomicInteger();
        var countingStatements = new JdbcSettlementStatementService(
                new JdbcTemplate(new CountingDataSource(dataSource, preparedStatements)),
                telemetry);
        var first = countingStatements.query(allFilter);
        assertThat(preparedStatements).hasValue(1);

        var repeated = statements.query(allFilter);
        List<UUID> firstIds = first.entries().stream()
                .map(SettlementStatementService.Entry::entryId)
                .toList();
        assertThat(first.entries()).hasSize(4);
        assertThat(firstIds).doesNotHaveDuplicates();
        assertThat(repeated.entries().stream()
                        .map(SettlementStatementService.Entry::entryId)
                        .toList())
                .containsExactlyElementsOf(firstIds);
        assertThat(first.entries().stream()
                        .map(SettlementStatementService.Entry::effectiveAt)
                        .distinct())
                .containsExactly(EFFECTIVE_AT);

        List<UUID> pagedIds = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            var one = statements.query(filter(null, null, null, null, null, null, page, 1));
            assertThat(one.entries()).hasSize(1);
            pagedIds.add(one.entries().getFirst().entryId());
            assertThat(one.hasNext()).isEqualTo(page < 3);
        }
        assertThat(pagedIds).containsExactlyElementsOf(firstIds);
        assertThat(statements.query(filter(null, null, null, null, null, null, 0, 25)).entries())
                .hasSize(4);
        assertThat(statements.query(allFilter).entries()).hasSize(4);

        assertThat(statements.query(filter(EFFECTIVE_AT, EFFECTIVE_AT.plusSeconds(1), null, null, null, null, 0, 100)).entries())
                .hasSize(4);
        assertThat(statements.query(filter(null, EFFECTIVE_AT, null, null, null, null, 0, 100)).entries())
                .isEmpty();
        assertThat(statements.query(filter(EFFECTIVE_AT.plusSeconds(1), null, null, null, null, null, 0, 100)).entries())
                .isEmpty();

        assertThat(statements.query(filter(null, null, assignorId, null, null, null, 0, 100)).entries())
                .hasSize(4);
        assertThat(statements.query(filter(null, null, null, "BRL", null, null, 0, 100)).entries())
                .hasSize(4);
        assertThat(statements.query(filter(null, null, null, null, "BRL", null, 0, 100)).entries())
                .hasSize(4);
        assertThat(statements.query(filter(null, null, null, null, null, "MERCANTILE_INVOICE", 0, 100)).entries())
                .hasSize(4);
        assertThat(statements.query(filter(null, null, UUID.randomUUID(), null, null, null, 0, 100)).entries())
                .isEmpty();
        assertThat(statements.query(filter(null, null, null, null, "USD", null, 0, 100)).entries())
                .isEmpty();
        assertThat(statements.query(filter(null, null, null, null, null, "POST_DATED_CHEQUE", 0, 100)).entries())
                .isEmpty();
        assertThat(statements.query(filter(
                                EFFECTIVE_AT,
                                EFFECTIVE_AT.plusSeconds(1),
                                assignorId,
                                "BRL",
                                "BRL",
                                "MERCANTILE_INVOICE",
                                0,
                                100))
                        .entries())
                .hasSize(4);
        assertThat(statements.query(filter(
                                EFFECTIVE_AT,
                                EFFECTIVE_AT.plusSeconds(1),
                                assignorId,
                                "BRL",
                                "USD",
                                "MERCANTILE_INVOICE",
                                0,
                                100))
                        .entries())
                .isEmpty();
        assertThatThrownBy(() -> statements.query(filter(
                        null,
                        null,
                        null,
                        "BRL' OR 1=1 --",
                        null,
                        null,
                        0,
                        100)))
                .isInstanceOf(UnsupportedCurrencyException.class);

        assertThat(first.entries().stream()
                        .filter(entry -> "SETTLEMENT".equals(entry.entryType()))
                        .map(SettlementStatementService.Entry::entryId))
                .doesNotContainAnyElementsOf(first.entries().stream()
                        .filter(entry -> "REVERSAL".equals(entry.entryType()))
                        .map(SettlementStatementService.Entry::entryId)
                        .toList());
    }

    @Test
    void rejectsAnOffsetBeyondTheDocumentedExerciseMaximum() {
        assertThatThrownBy(() -> statements.query(
                        filter(null, null, null, null, null, null, 10_001, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page offset is out of bounds");
    }

    private static final class CountingDataSource extends DelegatingDataSource {
        private final AtomicInteger preparedStatements;

        private CountingDataSource(DataSource targetDataSource, AtomicInteger preparedStatements) {
            super(targetDataSource);
            this.preparedStatements = preparedStatements;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return countingConnection(super.getConnection(), preparedStatements);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return countingConnection(super.getConnection(username, password), preparedStatements);
        }

        private static Connection countingConnection(
                Connection connection, AtomicInteger preparedStatements) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement")
                                || method.getName().equals("prepareCall")) {
                            preparedStatements.incrementAndGet();
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }

    private UUID createQuote(UUID assignorId, UUID receivableId) {
        receivables.register(new ReceivableService.RegisterCommand(
                receivableId,
                assignorId,
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.00"),
                "BRL",
                LocalDate.parse("2030-01-01"),
                LocalDate.parse("2030-02-14"),
                "operator@srm.local"));
        return pricing.createQuote(receivableId, "BRL", "operator@srm.local").id();
    }

    private static SettlementStatementService.Filter filter(
            Instant from,
            Instant to,
            UUID assignorId,
            String assetCurrency,
            String settlementCurrency,
            String productType,
            int page,
            int size) {
        return new SettlementStatementService.Filter(
                from,
                to,
                assignorId,
                assetCurrency,
                settlementCurrency,
                productType,
                page,
                size);
    }
}
