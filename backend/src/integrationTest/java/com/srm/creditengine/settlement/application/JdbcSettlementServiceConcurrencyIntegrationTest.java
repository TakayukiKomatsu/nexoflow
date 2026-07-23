package com.srm.creditengine.settlement.application;

import com.srm.creditengine.settlement.domain.AlreadySettledException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.receivable.application.ReceivableService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-PostgreSQL proof of the atomicity/idempotency/rollback contract described in
 * docs/sdd/05_sdd_settlement-preview-and-atomic-idempotent-settlement.md.
 *
 * {@link SettlementControllerTest} exercises the HTTP contract with a mocked service;
 * this class drives the same controller/filter/serialization path through MockMvc
 * while retaining the real service, transaction manager, SQL, and PostgreSQL schema.
 */
@Testcontainers
@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
@AutoConfigureMockMvc
class JdbcSettlementServiceConcurrencyIntegrationTest {
    private static final String ACTOR = "operator@srm.local";

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

    @TestConfiguration
    static class FaultInjectionConfig {
        @Bean
        @Primary
        JdbcTemplate faultInjectingJdbcTemplate(DataSource dataSource) {
            return new FaultInjectingJdbcTemplate(dataSource);
        }
    }

    /**
     * Throws immediately after the {@code settlements} row insert executes, on the calling
     * thread only, when armed. Simulates SDD test fault "after-settlement-insert" without
     * touching production service code.
     */
    static final class FaultInjectingJdbcTemplate extends JdbcTemplate {
        private static final ThreadLocal<String> ARMED = new ThreadLocal<>();
        private static final ThreadLocal<UUID> INSERTED_SETTLEMENT_ID = new ThreadLocal<>();

        FaultInjectingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        static void arm(String faultId) {
            INSERTED_SETTLEMENT_ID.remove();
            ARMED.set(faultId);
        }

        static void disarm() {
            ARMED.remove();
            INSERTED_SETTLEMENT_ID.remove();
        }

        static UUID insertedSettlementId() {
            return INSERTED_SETTLEMENT_ID.get();
        }

        @Override
        public int update(String sql, Object... args) {
            int result = super.update(sql, args);
            if ("after-settlement-insert".equals(ARMED.get()) && sql.startsWith("insert into settlements (")) {
                INSERTED_SETTLEMENT_ID.set((UUID) args[0]);
                ARMED.remove();
                throw new SettlementFaultInjectedException();
            }
            return result;
        }
    }

    static final class SettlementFaultInjectedException extends RuntimeException {
        SettlementFaultInjectedException() {
            super("SETTLE-ROLLBACK-008 injected failure after settlement insert");
        }
    }

    @Autowired SettlementService settlements;
    @Autowired AssignorService assignors;
    @Autowired ReceivableService receivables;
    @Autowired PricingService pricing;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtEncoder jwtEncoder;

    @BeforeEach
    void installClaimBoundaryBarrier() {
        jdbc.execute("""
                create or replace function task3_block_idempotency_claim() returns trigger
                language plpgsql as $$
                declare
                    barrier_token text;
                begin
                    if new.operation = 'SETTLEMENT_CREATE'
                            and new.idempotency_key like 'task3-claim-%:%' then
                        barrier_token := split_part(new.idempotency_key, ':', 1);
                        perform set_config('application_name', 'task3:' || barrier_token, true);
                        perform pg_advisory_xact_lock(hashtextextended(barrier_token, 0));
                    end if;
                    return new;
                end
                $$""");
        jdbc.execute("drop trigger if exists task3_block_idempotency_claim on idempotency_records");
        jdbc.execute("""
                create trigger task3_block_idempotency_claim
                before insert on idempotency_records
                for each row execute function task3_block_idempotency_claim()
                """);
    }

    @AfterEach
    void removeClaimBoundaryBarrierAndDisarmFault() {
        try {
            jdbc.execute("drop trigger if exists task3_block_idempotency_claim on idempotency_records");
            jdbc.execute("drop function if exists task3_block_idempotency_claim()");
        } finally {
            FaultInjectingJdbcTemplate.disarm();
        }
    }

    @Test
    void SETTLE_006_concurrentSameKeyRequestsProduceByteIdenticalHttpBodiesAndOneReplayHeader() throws Exception {
        UUID assignorId = newAssignor();
        UUID receivable1 = newReceivable(assignorId);
        UUID receivable2 = newReceivable(assignorId);
        UUID quote1 = pricing.createQuote(receivable1, "BRL", ACTOR).id();
        UUID quote2 = pricing.createQuote(receivable2, "BRL", ACTOR).id();
        List<UUID> orderedQuoteIds = List.of(quote1, quote2);
        String barrierToken = barrierToken();
        String idempotencyKey = claimKey(barrierToken, "same");
        String token = operatorToken();

        int settlementsBefore = rowCount("select count(*) from settlements where assignor_id=?", assignorId);
        int itemsBefore = rowCount(
                "select count(*) from settlement_items where quote_id in (?,?)", quote1, quote2);
        int idempotencyBefore = idempotencyRows(idempotencyKey);

        List<HttpAttempt> outcomes = raceAtClaimBoundary(
                barrierToken,
                () -> httpSettleAttempt(orderedQuoteIds, idempotencyKey, token),
                () -> httpSettleAttempt(orderedQuoteIds, idempotencyKey, token));
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
        assertThat(outcomes).extracting(HttpAttempt::status).containsOnly(201);

        HttpAttempt responseA = outcomes.get(0);
        HttpAttempt responseB = outcomes.get(1);
        assertThat(responseA.body()).isEqualTo(responseB.body());
        assertThat(outcomes)
                .extracting(HttpAttempt::replayHeader)
                .containsExactlyInAnyOrder(null, "true");

        JsonNode response = objectMapper.readTree(responseA.body());
        assertThat(response.path("items").size()).isEqualTo(orderedQuoteIds.size());
        assertThat(response.path("items").get(0).path("quoteId").asText())
                .isEqualTo(quote1.toString());
        assertThat(response.path("items").get(1).path("quoteId").asText())
                .isEqualTo(quote2.toString());

        UUID settlementId = UUID.fromString(response.path("settlementId").asText());
        assertThat(rowCount("select count(*) from settlements where id=?", settlementId)).isEqualTo(1);
        assertThat(rowCount("select count(*) from settlements where assignor_id=?", assignorId))
                .isEqualTo(settlementsBefore + 1);
        assertThat(rowCount("select count(*) from settlement_items where settlement_id=?", settlementId))
                .isEqualTo(2);
        assertThat(rowCount("select count(*) from settlement_items where quote_id in (?,?)", quote1, quote2))
                .isEqualTo(itemsBefore + 2);
        assertThat(idempotencyRows(idempotencyKey)).isEqualTo(idempotencyBefore + 1);
        assertThat(completedIdempotencyRows(idempotencyKey)).isEqualTo(1);
        assertThat(rowCount(
                        "select count(*) from audit_events where action='SETTLEMENT_CREATED' and target_id=?",
                        settlementId))
                .isEqualTo(1);
        assertThat(quoteStatus(quote1)).isEqualTo("CONSUMED");
        assertThat(quoteStatus(quote2)).isEqualTo("CONSUMED");
        assertThat(receivableStatus(receivable1)).isEqualTo("SETTLED");
        assertThat(receivableStatus(receivable2)).isEqualTo("SETTLED");
    }

    @Test
    void SETTLE_006_sameKeyWithDifferentOrderedBodyHasOneSuccessAndOneReuseFailure() throws Exception {
        UUID assignorId = newAssignor();
        UUID receivable1 = newReceivable(assignorId);
        UUID receivable2 = newReceivable(assignorId);
        UUID quote1 = pricing.createQuote(receivable1, "BRL", ACTOR).id();
        UUID quote2 = pricing.createQuote(receivable2, "BRL", ACTOR).id();
        List<UUID> firstOrder = List.of(quote1, quote2);
        List<UUID> secondOrder = List.of(quote2, quote1);
        String barrierToken = barrierToken();
        String idempotencyKey = claimKey(barrierToken, "same");

        int settlementsBefore = rowCount("select count(*) from settlements where assignor_id=?", assignorId);
        int itemsBefore = rowCount(
                "select count(*) from settlement_items where quote_id in (?,?)", quote1, quote2);
        int idempotencyBefore = idempotencyRows(idempotencyKey);

        List<SettlementAttempt> outcomes = raceAtClaimBoundary(
                barrierToken,
                () -> settleAttempt(firstOrder, idempotencyKey),
                () -> settleAttempt(secondOrder, idempotencyKey));
        List<SettlementAttempt> winners =
                outcomes.stream().filter(outcome -> outcome.error() == null).toList();
        List<SettlementAttempt> losers =
                outcomes.stream().filter(outcome -> outcome.error() != null).toList();
        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(1);
        assertThat(losers.getFirst().error()).isInstanceOf(IdempotencyKeyReusedException.class);

        UUID settlementId = winners.getFirst().result().settlementId();
        assertThat(winners.getFirst().result().replayed()).isFalse();
        assertThat(jdbc.query(
                        "select quote_id from settlement_items where settlement_id=? order by item_position",
                        (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
                        settlementId))
                .containsExactlyElementsOf(winners.getFirst().orderedQuoteIds());
        assertThat(rowCount("select count(*) from settlements where id=?", settlementId)).isEqualTo(1);
        assertThat(rowCount("select count(*) from settlements where assignor_id=?", assignorId))
                .isEqualTo(settlementsBefore + 1);
        assertThat(rowCount("select count(*) from settlement_items where settlement_id=?", settlementId))
                .isEqualTo(2);
        assertThat(rowCount("select count(*) from settlement_items where quote_id in (?,?)", quote1, quote2))
                .isEqualTo(itemsBefore + 2);
        assertThat(idempotencyRows(idempotencyKey)).isEqualTo(idempotencyBefore + 1);
        assertThat(completedIdempotencyRows(idempotencyKey)).isEqualTo(1);
        assertThat(rowCount(
                        "select count(*) from audit_events where action='SETTLEMENT_CREATED' and target_id=?",
                        settlementId))
                .isEqualTo(1);
    }

    @Test
    void SETTLE_006_reversedOverlappingBatchesUseOneStableLockOrderWithoutDeadlock() throws Exception {
        UUID assignorId = newAssignor();
        UUID receivable1 = newReceivable(assignorId);
        UUID receivable2 = newReceivable(assignorId);
        UUID quote1 = pricing.createQuote(receivable1, "BRL", ACTOR).id();
        UUID quote2 = pricing.createQuote(receivable2, "BRL", ACTOR).id();
        List<UUID> firstOrder = List.of(quote1, quote2);
        List<UUID> reversedOrder = List.of(quote2, quote1);
        String barrierToken = barrierToken();
        String keyA = claimKey(barrierToken, "ordered-a");
        String keyB = claimKey(barrierToken, "ordered-b");

        List<SettlementAttempt> outcomes = raceAtClaimBoundary(
                barrierToken,
                () -> settleAttempt(firstOrder, keyA),
                () -> settleAttempt(reversedOrder, keyB));

        List<SettlementAttempt> winners =
                outcomes.stream().filter(outcome -> outcome.error() == null).toList();
        List<SettlementAttempt> losers =
                outcomes.stream().filter(outcome -> outcome.error() != null).toList();
        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(1);
        assertThat(losers.getFirst().error()).isInstanceOf(AlreadySettledException.class);
        assertThat(jdbc.query(
                        "select quote_id from settlement_items where settlement_id=? order by item_position",
                        (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
                        winners.getFirst().result().settlementId()))
                .containsExactlyElementsOf(winners.getFirst().orderedQuoteIds());
        assertThat(quoteStatus(quote1)).isEqualTo("CONSUMED");
        assertThat(quoteStatus(quote2)).isEqualTo("CONSUMED");
    }

    @Test
    void SETTLE_006_concurrentDifferentKeysRacingTheSameReceivableOnlyOneSucceeds() throws Exception {
        UUID assignorId = newAssignor();
        UUID receivableId = newReceivable(assignorId);
        UUID quoteId = pricing.createQuote(receivableId, "BRL", ACTOR).id();
        List<UUID> orderedQuoteIds = List.of(quoteId);
        String barrierToken = barrierToken();
        String keyA = claimKey(barrierToken, UUID.randomUUID().toString());
        String keyB = claimKey(barrierToken, UUID.randomUUID().toString());

        int settlementsBefore = rowCount("select count(*) from settlements where assignor_id=?", assignorId);
        int itemsBefore = rowCount("select count(*) from settlement_items where quote_id=?", quoteId);
        int idempotencyBefore = rowCount(
                "select count(*) from idempotency_records where actor=? and operation='SETTLEMENT_CREATE' "
                        + "and idempotency_key in (?,?)",
                ACTOR, keyA, keyB);

        List<SettlementAttempt> outcomes = raceAtClaimBoundary(
                barrierToken,
                () -> settleAttempt(orderedQuoteIds, keyA),
                () -> settleAttempt(orderedQuoteIds, keyB));
        List<SettlementAttempt> winners =
                outcomes.stream().filter(outcome -> outcome.error() == null).toList();
        List<SettlementAttempt> losers =
                outcomes.stream().filter(outcome -> outcome.error() != null).toList();
        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(1);
        assertThat(losers.getFirst().error()).isInstanceOf(AlreadySettledException.class);

        UUID settlementId = winners.getFirst().result().settlementId();
        assertThat(rowCount("select count(*) from settlements where id=?", settlementId)).isEqualTo(1);
        assertThat(rowCount("select count(*) from settlements where assignor_id=?", assignorId))
                .isEqualTo(settlementsBefore + 1);
        assertThat(rowCount("select count(*) from settlement_items where settlement_id=?", settlementId))
                .isEqualTo(1);
        assertThat(rowCount("select count(*) from settlement_items where quote_id=?", quoteId))
                .isEqualTo(itemsBefore + 1);
        assertThat(rowCount(
                        "select count(*) from idempotency_records where actor=? and operation='SETTLEMENT_CREATE' "
                                + "and idempotency_key in (?,?)",
                        ACTOR, keyA, keyB))
                .isEqualTo(idempotencyBefore + 1);
        assertThat(rowCount(
                        "select count(*) from audit_events where action='SETTLEMENT_CREATED' and target_id=?",
                        settlementId))
                .isEqualTo(1);
        assertThat(quoteStatus(quoteId)).isEqualTo("CONSUMED");
        assertThat(receivableStatus(receivableId)).isEqualTo("SETTLED");
        Long version = jdbc.queryForObject(
                "select version from receivables where id=?", Long.class, receivableId);
        assertThat(version).isEqualTo(1L);
    }

    @Test
    void SETTLE_ROLLBACK_008_injectedFailureAfterSettlementInsertLeavesNoPartialState() {
        UUID assignorId = newAssignor();
        UUID receivableId = newReceivable(assignorId);
        UUID quoteId = pricing.createQuote(receivableId, "BRL", ACTOR).id();
        String idempotencyKey = "rollback-008-" + UUID.randomUUID();

        int settlementsBefore = rowCount(
                "select count(*) from settlements where assignor_id=? and created_by=?", assignorId, ACTOR);
        int itemsBefore = rowCount("select count(*) from settlement_items where quote_id=?", quoteId);
        int idempotencyBefore = idempotencyRows(idempotencyKey);
        int completedIdempotencyBefore = completedIdempotencyRows(idempotencyKey);

        FaultInjectingJdbcTemplate.arm("after-settlement-insert");
        assertThatThrownBy(() -> settlements.settle(List.of(quoteId), idempotencyKey, ACTOR))
                .isInstanceOf(SettlementFaultInjectedException.class);

        UUID attemptedSettlementId = FaultInjectingJdbcTemplate.insertedSettlementId();
        assertThat(attemptedSettlementId).isNotNull();
        assertThat(rowCount("select count(*) from settlements where id=?", attemptedSettlementId)).isZero();
        assertThat(rowCount("select count(*) from settlements where assignor_id=? and created_by=?", assignorId, ACTOR))
                .isEqualTo(settlementsBefore);
        assertThat(rowCount("select count(*) from settlement_items where settlement_id=?", attemptedSettlementId))
                .isZero();
        assertThat(rowCount("select count(*) from settlement_items where quote_id=?", quoteId))
                .isEqualTo(itemsBefore);
        assertThat(idempotencyRows(idempotencyKey)).isEqualTo(idempotencyBefore);
        assertThat(completedIdempotencyRows(idempotencyKey))
                .isEqualTo(completedIdempotencyBefore);
        assertThat(rowCount(
                        "select count(*) from audit_events where action='SETTLEMENT_CREATED' and target_id=?",
                        attemptedSettlementId))
                .isZero();
        assertThat(quoteStatus(quoteId)).isEqualTo("ACTIVE");
        assertThat(receivableStatus(receivableId)).isEqualTo("REGISTERED");
        Long version = jdbc.queryForObject(
                "select version from receivables where id=?", Long.class, receivableId);
        assertThat(version).isEqualTo(0L);
    }

    private <T> List<T> raceAtClaimBoundary(
            String barrierToken, Callable<T> firstTask, Callable<T> secondTask) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<T> first = null;
        Future<T> second = null;
        try (Connection control = dataSource.getConnection()) {
            setAdvisoryLock(control, "pg_advisory_lock", barrierToken);
            boolean locked = true;
            boolean completedNormally = false;
            try {
                first = executor.submit(firstTask);
                second = executor.submit(secondTask);
                awaitBlockedClaims(barrierToken, 2);
                setAdvisoryLock(control, "pg_advisory_unlock", barrierToken);
                locked = false;
                List<T> results = List.of(
                        first.get(15, TimeUnit.SECONDS),
                        second.get(15, TimeUnit.SECONDS));
                completedNormally = true;
                return results;
            } finally {
                try {
                    if (!completedNormally) {
                        terminateBlockedClaimBackends(barrierToken);
                    }
                } finally {
                    if (locked) {
                        setAdvisoryLock(control, "pg_advisory_unlock", barrierToken);
                    }
                }
            }
        } finally {
            if (first != null && !first.isDone()) {
                first.cancel(true);
            }
            if (second != null && !second.isDone()) {
                second.cancel(true);
            }
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent settlement workers did not terminate");
            }
        }
    }

    private void terminateBlockedClaimBackends(String barrierToken) {
        jdbc.query(
                "select pg_terminate_backend(pid) from pg_stat_activity "
                        + "where application_name=? and pid<>pg_backend_pid()",
                (resultSet, rowNumber) -> resultSet.getBoolean(1),
                "task3:" + barrierToken);
    }

    private SettlementAttempt settleAttempt(List<UUID> orderedQuoteIds, String idempotencyKey) {
        try {
            return new SettlementAttempt(
                    orderedQuoteIds,
                    settlements.settle(orderedQuoteIds, idempotencyKey, ACTOR),
                    null);
        } catch (RuntimeException exception) {
            return new SettlementAttempt(orderedQuoteIds, null, exception);
        }
    }

    private HttpAttempt httpSettleAttempt(
            List<UUID> orderedQuoteIds, String idempotencyKey, String token) {
        try {
            var response = mockMvc.perform(post("/api/v1/settlements")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(
                                    new SettlementRequest(orderedQuoteIds))))
                    .andReturn()
                    .getResponse();
            return new HttpAttempt(
                    response.getStatus(),
                    response.getContentAsByteArray(),
                    response.getHeader("Idempotent-Replay"),
                    null);
        } catch (Exception exception) {
            return new HttpAttempt(0, new byte[0], null, exception);
        }
    }

    private void awaitBlockedClaims(String barrierToken, int expectedClaims) {
        String applicationName = "task3:" + barrierToken;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int observedClaims;
        do {
            observedClaims = rowCount(
                    "select count(*) from pg_stat_activity where application_name=? "
                            + "and state='active' and wait_event_type='Lock'",
                    applicationName);
            if (observedClaims == expectedClaims) {
                return;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        throw new AssertionError(
                "Expected " + expectedClaims + " blocked idempotency claims but observed " + observedClaims);
    }

    private void setAdvisoryLock(Connection connection, String function, String barrierToken)
            throws Exception {
        try (var statement =
                connection.prepareStatement("select " + function + "(hashtextextended(?, 0))")) {
            statement.setString(1, barrierToken);
            statement.execute();
        }
    }

    private String barrierToken() {
        return "task3-claim-" + UUID.randomUUID();
    }

    private String claimKey(String barrierToken, String suffix) {
        return barrierToken + ":" + suffix;
    }

    private String operatorToken() {
        Instant issuedAt = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("srm-credit-engine")
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(600))
                .claim("email", ACTOR)
                .claim("roles", List.of("OPERATOR"))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private int idempotencyRows(String idempotencyKey) {
        return rowCount(
                "select count(*) from idempotency_records where actor=? "
                        + "and operation='SETTLEMENT_CREATE' and idempotency_key=?",
                ACTOR, idempotencyKey);
    }

    private int completedIdempotencyRows(String idempotencyKey) {
        return rowCount(
                "select count(*) from idempotency_records where actor=? "
                        + "and operation='SETTLEMENT_CREATE' and idempotency_key=? and status='COMPLETED'",
                ACTOR, idempotencyKey);
    }

    private record SettlementRequest(List<UUID> quoteIds) {}

    private record HttpAttempt(
            int status, byte[] body, String replayHeader, Exception error) {}

    private record SettlementAttempt(
            List<UUID> orderedQuoteIds, SettlementService.Result result, RuntimeException error) {}

    private UUID newAssignor() {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(assignorId, "Concurrency Co", "CCY" + assignorId.toString().substring(0, 8), true, ACTOR));
        return assignorId;
    }

    private UUID newReceivable(UUID assignorId) {
        UUID receivableId = UUID.randomUUID();
        receivables.register(new ReceivableService.RegisterCommand(receivableId, assignorId, "MERCANTILE_INVOICE", new BigDecimal("1000.00"), "BRL", LocalDate.parse("2030-01-01"), LocalDate.parse("2030-02-14"), ACTOR));
        return receivableId;
    }

    private String quoteStatus(UUID quoteId) {
        return jdbc.queryForObject("select status from pricing_quotes where id=?", String.class, quoteId);
    }

    private String receivableStatus(UUID receivableId) {
        return jdbc.queryForObject("select status from receivables where id=?", String.class, receivableId);
    }

    private int rowCount(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
