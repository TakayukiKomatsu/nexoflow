package com.srm.creditengine.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.srm.creditengine.settlement.application.AlreadyReversedException;
import com.srm.creditengine.settlement.application.AlreadySettledException;
import com.srm.creditengine.settlement.domain.LockedQuote;
import com.srm.creditengine.settlement.domain.LockedSettlement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcSettlementRepositoryTest {
    @Test
    void rejectsConflictingQuoteOrReceivableConsumption() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("update pricing_quotes"), any(Object[].class))).thenReturn(0);
        var repository = new JdbcSettlementRepository(jdbc);

        assertThatThrownBy(() -> repository.consumeQuoteAndReceivable(quote()))
                .isInstanceOf(AlreadySettledException.class);
    }

    @Test
    void rejectsMissingLockedSettlement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(startsWith("select id from settlements"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        var repository = new JdbcSettlementRepository(jdbc);

        assertThatThrownBy(() -> repository.lockSettlement(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Settlement not found");
    }

    @Test
    void rejectsReversalWhenAnItemNoLongerHasSettledState() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("insert into settlement_reversals"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("update receivables"), any(Object[].class))).thenReturn(0);
        var repository = new JdbcSettlementRepository(jdbc);

        assertThatThrownBy(() -> repository.reverse(
                new LockedSettlement(UUID.randomUUID(), List.of(UUID.randomUUID())), "reason", Instant.now(), "operator"))
                .isInstanceOf(AlreadyReversedException.class);
    }

    private static LockedQuote quote() {
        return new LockedQuote(UUID.randomUUID(), UUID.randomUUID(), "BRL", BigDecimal.TEN, Instant.now().plusSeconds(60),
                "ACTIVE", UUID.randomUUID(), "REGISTERED", 0, "BRL", "MERCANTILE_INVOICE");
    }
}
