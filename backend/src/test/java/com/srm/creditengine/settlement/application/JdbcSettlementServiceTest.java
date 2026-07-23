package com.srm.creditengine.settlement.application;

import com.srm.creditengine.settlement.domain.AlreadySettledException;
import com.srm.creditengine.settlement.domain.PricingQuoteExpiredException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import com.srm.creditengine.shared.domain.DomainResourceNotFoundException;
import com.srm.creditengine.settlement.infrastructure.JdbcAuditEventRecorder;
import com.srm.creditengine.settlement.infrastructure.JdbcIdempotencyRepository;
import com.srm.creditengine.settlement.infrastructure.JdbcSettlementRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings({"rawtypes", "unchecked"})
class JdbcSettlementServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-15T12:00:00Z");
    private static final UUID ASSIGNOR = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID QUOTE = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID RECEIVABLE = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Test
    void previewRejectsMissingAndDuplicateQuoteIdsBeforeQuerying() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SettlementService service = service(jdbc);

        assertThatThrownBy(() -> service.preview(null, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one pricing quote is required");
        assertThatThrownBy(() -> service.preview(List.of(), "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one pricing quote is required");
        assertThatThrownBy(() -> service.preview(List.of(QUOTE, QUOTE), "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pricing quote IDs must be ordered and unique");
        assertThatThrownBy(() -> service.preview(java.util.Arrays.asList((UUID) null), "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pricing quote IDs must be ordered and unique");
    }

    @Test
    void previewRejectsEveryInvalidQuoteLifecycleState() {
        assertPreviewFailure(row("CONSUMED", "REGISTERED", ASSIGNOR, "BRL"), AlreadySettledException.class);
        assertPreviewFailure(row("ACTIVE", "REGISTERED", ASSIGNOR, "BRL", NOW), PricingQuoteExpiredException.class);
        assertPreviewFailure(row("ACTIVE", "SETTLED", ASSIGNOR, "BRL"), AlreadySettledException.class);
    }

    @Test
    void previewRejectsMissingQuotesAndMixedAssignorOrCurrencyBatches() {
        JdbcTemplate missingJdbc = mock(JdbcTemplate.class);
        stubQuotes(missingJdbc, List.of());
        assertThatThrownBy(() -> service(missingJdbc).preview(List.of(QUOTE), "operator"))
                .isInstanceOf(DomainResourceNotFoundException.class)
                .hasMessage("The requested domain resource was not found.");

        UUID secondQuote = UUID.fromString("00000000-0000-0000-0000-000000000202");
        assertMixedBatchFailure(List.of(
                row("ACTIVE", "REGISTERED", ASSIGNOR, "BRL"),
                row(secondQuote, "ACTIVE", "REGISTERED", UUID.fromString("00000000-0000-0000-0000-000000000102"), "BRL")));
        assertMixedBatchFailure(List.of(
                row("ACTIVE", "REGISTERED", ASSIGNOR, "BRL"),
                row(secondQuote, "ACTIVE", "REGISTERED", ASSIGNOR, "USD")));
    }

    @Test
    void getAndReverseRejectMissingRequiredIdentifiersAndReason() {
        SettlementService service = service(mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.get(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Settlement ID is required");
        assertThatThrownBy(() -> service.reverse(null, "reason", "key", "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reversal reason is required");
        assertThatThrownBy(() -> service.reverse(QUOTE, " ", "key", "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reversal reason is required");
        assertThatThrownBy(() -> service.reverse(QUOTE, "x".repeat(501), "key", "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reversal reason is required");
    }

    @Test
    void settlementRejectsAReusedIdempotencyKeyBeforeValidatingQuotes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubIdempotency(jdbc, "different-hash");

        assertThatThrownBy(() -> service(jdbc).settle(List.of(QUOTE), "key", "operator"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void getLoadsAnExistingSettlement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID settlementId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        when(jdbc.query(startsWith("select settlement_currency_code"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> map(
                        invocation.getArgument(1),
                        resultSet("BRL", new BigDecimal("100.00"), "COMPLETED", NOW)));
        when(jdbc.query(startsWith("select quote_id"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> map(
                        invocation.getArgument(1),
                        itemResultSet(QUOTE, RECEIVABLE, new BigDecimal("100.00"))));

        org.assertj.core.api.Assertions.assertThat(service(jdbc).get(settlementId).status())
                .isEqualTo("COMPLETED");
    }

    @Test
    void reversalAcceptsAWellFormedReasonUntilIdempotencyValidation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubReversalIdempotency(jdbc, "different-hash");
        SettlementService service = service(jdbc);

        assertThatThrownBy(() -> service.reverse(QUOTE, null, "key", "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reversal reason is required");
        assertThatThrownBy(() -> service.reverse(QUOTE, "reversal reason", "key", "operator"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void previewTreatsNonActiveQuotesAsExpired() {
        assertPreviewFailure(row("PENDING", "REGISTERED", ASSIGNOR, "BRL"), PricingQuoteExpiredException.class);
    }

    private static void assertPreviewFailure(QuoteRow quote, Class<? extends RuntimeException> type) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubQuotes(jdbc, List.of(quote));
        assertThatThrownBy(() -> service(jdbc).preview(List.of(QUOTE), "operator"))
                .isInstanceOf(type);
    }

    private static void assertMixedBatchFailure(List<QuoteRow> quotes) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubQuotes(jdbc, quotes);
        assertThatThrownBy(() -> service(jdbc).preview(List.of(QUOTE, quotes.get(1).id()), "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pricing quotes must have one assignor and settlement currency");
    }

    private static SettlementService service(JdbcTemplate jdbc) {
        return new SettlementApplicationService(
                new JdbcSettlementRepository(jdbc),
                new JdbcIdempotencyRepository(jdbc),
                new JdbcAuditEventRecorder(jdbc),
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(FinancialTelemetry.class));
    }

    private static void stubIdempotency(JdbcTemplate jdbc, String hash) {
        when(jdbc.query(startsWith("select id,request_hash,settlement_id,reversal_id,status"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> map(
                        invocation.getArgument(1),
                        idempotencyResultSet(hash)));
    }

    private static void stubReversalIdempotency(JdbcTemplate jdbc, String hash) {
        when(jdbc.query(startsWith("select id,request_hash,settlement_id,reversal_id,status"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> map(
                        invocation.getArgument(1),
                        idempotencyResultSet(hash)));
    }

    private static List<?> map(RowMapper mapper, ResultSet resultSet) throws Exception {
        return List.of(mapper.mapRow(resultSet, 0));
    }

    private static ResultSet idempotencyResultSet(String hash) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1, UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getString(2)).thenReturn(hash);
        when(rs.getObject(3, UUID.class)).thenReturn(null);
        when(rs.getObject(4, UUID.class)).thenReturn(null);
        when(rs.getString(5)).thenReturn("PROCESSING");
        return rs;
    }

    private static ResultSet resultSet(
            String currency, BigDecimal amount, String status, Instant completedAt) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(currency);
        when(rs.getBigDecimal(2)).thenReturn(amount);
        when(rs.getString(3)).thenReturn(status);
        when(rs.getTimestamp(4)).thenReturn(java.sql.Timestamp.from(completedAt));
        return rs;
    }

    private static ResultSet itemResultSet(UUID quoteId, UUID receivableId, BigDecimal amount)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1, UUID.class)).thenReturn(quoteId);
        when(rs.getObject(2, UUID.class)).thenReturn(receivableId);
        when(rs.getBigDecimal(3)).thenReturn(amount);
        return rs;
    }

    private static void stubQuotes(JdbcTemplate jdbc, List<QuoteRow> rows) {
        when(jdbc.query(startsWith("select q.id"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return rows.stream().map(row -> {
                        try {
                            return mapper.mapRow(row.resultSet(), 0);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    }).toList();
                });
    }

    private static QuoteRow row(String quoteStatus, String receivableStatus, UUID assignor, String currency) {
        return row(QUOTE, quoteStatus, receivableStatus, assignor, currency, NOW.plusSeconds(60));
    }

    private static QuoteRow row(String quoteStatus, String receivableStatus, UUID assignor, String currency, Instant expiresAt) {
        return row(QUOTE, quoteStatus, receivableStatus, assignor, currency, expiresAt);
    }

    private static QuoteRow row(UUID id, String quoteStatus, String receivableStatus, UUID assignor, String currency) {
        return row(id, quoteStatus, receivableStatus, assignor, currency, NOW.plusSeconds(60));
    }

    private static QuoteRow row(UUID id, String quoteStatus, String receivableStatus, UUID assignor, String currency, Instant expiresAt) {
        return new QuoteRow(id, UUID.fromString("00000000-0000-0000-0000-0000000003" + id.toString().substring(id.toString().length() - 2)), quoteStatus, receivableStatus, assignor, currency, expiresAt);
    }

    private record QuoteRow(UUID id, UUID receivableId, String quoteStatus, String receivableStatus, UUID assignorId, String currency, Instant expiresAt) {
        ResultSet resultSet() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject(1, UUID.class)).thenReturn(id);
            when(rs.getObject(2, UUID.class)).thenReturn(receivableId);
            when(rs.getString(3)).thenReturn(currency);
            when(rs.getBigDecimal(4)).thenReturn(new BigDecimal("100.00"));
            when(rs.getTimestamp(5)).thenReturn(java.sql.Timestamp.from(expiresAt));
            when(rs.getString(6)).thenReturn(quoteStatus);
            when(rs.getObject(7, UUID.class)).thenReturn(assignorId);
            when(rs.getString(8)).thenReturn(receivableStatus);
            when(rs.getLong(9)).thenReturn(0L);
            when(rs.getString(10)).thenReturn("BRL");
            when(rs.getString(11)).thenReturn("MERCANTILE_INVOICE");
            return rs;
        }
    }
}
