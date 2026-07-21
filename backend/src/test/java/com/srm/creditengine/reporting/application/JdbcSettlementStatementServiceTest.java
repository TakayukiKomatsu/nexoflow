package com.srm.creditengine.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("unchecked")
class JdbcSettlementStatementServiceTest {
    private static final Instant FROM = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2030-02-01T00:00:00Z");

    @Test
    void rejectsInvalidDateRangesPageBoundsAndOverflowBeforeQuerying() {
        JdbcSettlementStatementService service = service(mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.query(filter(TO, FROM, 0, 50)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before to");
        assertThatThrownBy(() -> service.query(filter(null, null, -1, 50)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page and size are out of bounds");
        assertThatThrownBy(() -> service.query(filter(null, null, 0, 101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page and size are out of bounds");
        assertThatThrownBy(() -> service.query(filter(null, null, Integer.MAX_VALUE, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page offset is out of bounds");
        assertThatThrownBy(() -> service.query(filter(null, null, 20_001, 50)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page offset is out of bounds");
    }

    @Test
    void returnsOnlyTheRequestedPageAndSignalsTheFollowingPage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SettlementStatementService.Entry first = entry("00000000-0000-0000-0000-000000000001");
        SettlementStatementService.Entry second = entry("00000000-0000-0000-0000-000000000002");
        SettlementStatementService.Entry extra = entry("00000000-0000-0000-0000-000000000003");
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(first, second, extra));
        JdbcSettlementStatementService service = service(jdbc);

        var result = service.query(new SettlementStatementService.Filter(
                FROM,
                TO,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "BRL",
                "USD",
                "MERCANTILE_INVOICE",
                1,
                2));

        assertThat(result.entries()).containsExactly(first, second);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
    }

    private static JdbcSettlementStatementService service(JdbcTemplate jdbc) {
        return new JdbcSettlementStatementService(jdbc, mock(FinancialTelemetry.class));
    }

    private static SettlementStatementService.Filter filter(Instant from, Instant to, int page, int size) {
        return new SettlementStatementService.Filter(from, to, null, null, null, null, page, size);
    }

    private static SettlementStatementService.Entry entry(String id) {
        return new SettlementStatementService.Entry(
                UUID.fromString(id),
                "SETTLEMENT",
                BigDecimal.ONE,
                FROM,
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                null,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "BRL",
                "BRL",
                "MERCANTILE_INVOICE",
                UUID.fromString("00000000-0000-0000-0000-000000000301"));
    }
}
