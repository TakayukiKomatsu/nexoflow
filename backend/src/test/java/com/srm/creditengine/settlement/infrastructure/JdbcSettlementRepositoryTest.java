package com.srm.creditengine.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.srm.creditengine.settlement.application.SettlementRepository;
import com.srm.creditengine.settlement.domain.AlreadyReversedException;
import com.srm.creditengine.settlement.domain.AlreadySettledException;
import com.srm.creditengine.settlement.domain.LockedQuote;
import com.srm.creditengine.settlement.domain.LockedReceivable;
import com.srm.creditengine.settlement.domain.LockedSettlement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcSettlementRepositoryTest {
    @Test
    void exposesASeparateNonLockingPreviewRead() throws Exception {
        assertThat(SettlementRepository.class.getMethod("findQuotes", List.class)).isNotNull();
    }

    @Test
    void locksReceivablesGloballyBeforeLockingTheRequestedQuotes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        var repository = new JdbcSettlementRepository(jdbc);

        repository.lockQuotes(List.of(UUID.randomUUID(), UUID.randomUUID()));

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getAllValues().get(0))
                .startsWith("select r.id from receivables r")
                .contains("select q.receivable_id from pricing_quotes q")
                .endsWith("order by r.id for update");
        assertThat(sql.getAllValues().get(1))
                .contains("order by q.id")
                .endsWith("for update of q")
                .doesNotContain("for update of q, r");
    }

    @Test
    void previewReadsUseStableOrderWithoutTakingDatabaseLocks() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        var repository = new JdbcSettlementRepository(jdbc);

        repository.findQuotes(List.of(UUID.randomUUID(), UUID.randomUUID()));

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
                .contains("order by r.id,q.id")
                .doesNotContainIgnoringCase("for update");
    }

    @Test
    void rejectsConflictingQuoteOrReceivableConsumption() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("update pricing_quotes"), any(Object[].class))).thenReturn(0);
        var repository = new JdbcSettlementRepository(jdbc);

        assertThatThrownBy(() -> repository.consumeQuoteAndReceivable(quote()))
                .isInstanceOf(AlreadySettledException.class);
    }

    @Test
    void rejectsWhenQuoteConsumptionWinsButReceivableConsumptionLoses() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("update pricing_quotes"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("update receivables"), any(Object[].class))).thenReturn(0);
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
                .isInstanceOf(com.srm.creditengine.shared.domain.DomainResourceNotFoundException.class);
    }

    @Test
    void locksReversalReceivablesInTheGlobalUuidOrder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID settlementId = UUID.randomUUID();
        UUID lowerReceivable = UUID.fromString("00000000-0000-4000-8000-000000000041");
        UUID higherReceivable = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffe1");
        var lower = new LockedReceivable(lowerReceivable, 3L);
        var higher = new LockedReceivable(higherReceivable, 7L);
        when(jdbc.query(
                        startsWith("select id from settlements"),
                        any(RowMapper.class),
                        any(Object[].class)))
                .thenReturn(List.of(settlementId));
        when(jdbc.queryForObject(
                        startsWith("select count(*) from settlement_reversals"),
                        org.mockito.ArgumentMatchers.eq(Integer.class),
                        any(Object[].class)))
                .thenReturn(0);
        when(jdbc.query(
                        startsWith("select r.id,r.version from receivables r"),
                        any(RowMapper.class),
                        any(Object[].class)))
                .thenReturn(List.of(lower, higher));
        var repository = new JdbcSettlementRepository(jdbc);

        LockedSettlement locked = repository.lockSettlement(settlementId);

        assertThat(locked.receivables()).containsExactly(lower, higher);
        verify(jdbc).query(
                argThat(sql -> sql.startsWith("select r.id,r.version from receivables r")
                        && sql.contains("join settlement_items")
                        && sql.contains("order by r.id")
                        && sql.endsWith("for update of r")),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    void rejectsASettlementHeaderThatHasNoLockableItems() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID settlementId = UUID.randomUUID();
        when(jdbc.query(
                        startsWith("select id from settlements"),
                        any(RowMapper.class),
                        any(Object[].class)))
                .thenReturn(List.of(settlementId));
        when(jdbc.queryForObject(
                        startsWith("select count(*) from settlement_reversals"),
                        org.mockito.ArgumentMatchers.eq(Integer.class),
                        any(Object[].class)))
                .thenReturn(0);
        when(jdbc.query(
                        startsWith("select r.id,r.version from receivables r"),
                        any(RowMapper.class),
                        any(Object[].class)))
                .thenReturn(List.of());
        var repository = new JdbcSettlementRepository(jdbc);

        assertThatThrownBy(() -> repository.lockSettlement(settlementId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Settlement has no items");
    }

    @Test
    void rejectsReversalWhenAnItemNoLongerHasSettledState() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("insert into settlement_reversals"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("update receivables"), any(Object[].class))).thenReturn(0);
        var repository = new JdbcSettlementRepository(jdbc);

        assertThatThrownBy(() -> repository.reverse(
                new LockedSettlement(UUID.randomUUID(), List.of(new LockedReceivable(UUID.randomUUID(), 4L))), "reason", Instant.now(), "operator"))
                .isInstanceOf(AlreadyReversedException.class);
    }

    @Test
    void reversalComparesEveryLockedReceivableVersion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID settlementId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        var locked = new LockedReceivable(receivableId, 7L);
        when(jdbc.update(startsWith("insert into settlement_reversals"), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.update(
                eq("update receivables set status='REVERSED', version=version+1 "
                        + "where id=? and status='SETTLED' and version=?"),
                eq(receivableId),
                eq(7L)))
                .thenReturn(0);
        var repository = new JdbcSettlementRepository(jdbc);

        assertThatThrownBy(() -> repository.reverse(
                new LockedSettlement(settlementId, List.of(locked)),
                "reason",
                Instant.parse("2030-01-15T12:00:00Z"),
                "operator@srm.local"))
                .isInstanceOf(AlreadyReversedException.class);
    }

    private static LockedQuote quote() {
        return new LockedQuote(UUID.randomUUID(), UUID.randomUUID(), "BRL", BigDecimal.TEN, Instant.now().plusSeconds(60),
                "ACTIVE", UUID.randomUUID(), "REGISTERED", 0, "BRL", "MERCANTILE_INVOICE");
    }
}
