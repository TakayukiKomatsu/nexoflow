package com.srm.creditengine.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.ExchangeRateAuditRecorder;
import com.srm.creditengine.currency.application.ReferenceRateAuditRecorder;
import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.currency.domain.FxObservation;
import com.srm.creditengine.currency.infrastructure.JdbcExchangeRateAuditRecorder;
import com.srm.creditengine.currency.infrastructure.JdbcReferenceRateAuditRecorder;
import com.srm.creditengine.pricing.application.PricingAuditRecorder;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.pricing.domain.PricingQuoteSnapshot;
import com.srm.creditengine.pricing.infrastructure.JdbcPricingAuditRecorder;
import com.srm.creditengine.receivable.application.ReceivableService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-PostgreSQL evidence that each financial/reference mutation and its audit event
 * share the Spring-managed application transaction. Each fault recorder writes the
 * audit row through the production adapter and then throws, so both inserts must be
 * absent after the proxied service call returns.
 */
@Testcontainers
@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
class FinancialAuditAtomicityIntegrationTest {

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

    @TestConfiguration(proxyBeanMethods = false)
    static class AuditFailureConfiguration {
        @Bean
        @Primary
        FaultInjectingExchangeRateAuditRecorder faultInjectingExchangeRateAuditRecorder(
                JdbcExchangeRateAuditRecorder delegate) {
            return new FaultInjectingExchangeRateAuditRecorder(delegate);
        }

        @Bean
        @Primary
        FaultInjectingReferenceRateAuditRecorder faultInjectingReferenceRateAuditRecorder(
                JdbcReferenceRateAuditRecorder delegate) {
            return new FaultInjectingReferenceRateAuditRecorder(delegate);
        }

        @Bean
        @Primary
        FaultInjectingPricingAuditRecorder faultInjectingPricingAuditRecorder(
                JdbcPricingAuditRecorder delegate) {
            return new FaultInjectingPricingAuditRecorder(delegate);
        }
    }

    static final class FaultInjectingExchangeRateAuditRecorder implements ExchangeRateAuditRecorder {
        private final JdbcExchangeRateAuditRecorder delegate;
        private final AtomicBoolean failNext = new AtomicBoolean();

        FaultInjectingExchangeRateAuditRecorder(JdbcExchangeRateAuditRecorder delegate) {
            this.delegate = delegate;
        }

        void failNext() {
            failNext.set(true);
        }

        void disarm() {
            failNext.set(false);
        }

        @Override
        public void record(String actor, UUID targetId, FxObservation observation, Instant occurredAt) {
            delegate.record(actor, targetId, observation, occurredAt);
            if (failNext.getAndSet(false)) {
                throw new InjectedAuditFailure("exchange rate");
            }
        }
    }

    static final class FaultInjectingReferenceRateAuditRecorder implements ReferenceRateAuditRecorder {
        private final JdbcReferenceRateAuditRecorder delegate;
        private final AtomicReference<String> actionToFail = new AtomicReference<>();

        FaultInjectingReferenceRateAuditRecorder(JdbcReferenceRateAuditRecorder delegate) {
            this.delegate = delegate;
        }

        void failNext(String action) {
            actionToFail.set(action);
        }

        void disarm() {
            actionToFail.set(null);
        }

        @Override
        public void record(
                String actor, String action, String targetType, UUID targetId, Instant occurredAt) {
            delegate.record(actor, action, targetType, targetId, occurredAt);
            String expectedAction = actionToFail.get();
            if (action.equals(expectedAction) && actionToFail.compareAndSet(expectedAction, null)) {
                throw new InjectedAuditFailure(action);
            }
        }
    }

    static final class FaultInjectingPricingAuditRecorder implements PricingAuditRecorder {
        private final JdbcPricingAuditRecorder delegate;
        private final AtomicBoolean failNext = new AtomicBoolean();

        FaultInjectingPricingAuditRecorder(JdbcPricingAuditRecorder delegate) {
            this.delegate = delegate;
        }

        void failNext() {
            failNext.set(true);
        }

        void disarm() {
            failNext.set(false);
        }

        @Override
        public void recordQuoteCreated(String actor, PricingQuoteSnapshot snapshot) {
            delegate.recordQuoteCreated(actor, snapshot);
            if (failNext.getAndSet(false)) {
                throw new InjectedAuditFailure("pricing quote");
            }
        }
    }

    static final class InjectedAuditFailure extends RuntimeException {
        InjectedAuditFailure(String mutation) {
            super("Injected audit failure after " + mutation + " audit insert");
        }
    }

    @Autowired CurrencyService currency;
    @Autowired ReferenceRateService referenceRates;
    @Autowired PricingService pricing;
    @Autowired AssignorService assignors;
    @Autowired ReceivableService receivables;
    @Autowired JdbcTemplate jdbc;
    @Autowired FaultInjectingExchangeRateAuditRecorder exchangeRateAudit;
    @Autowired FaultInjectingReferenceRateAuditRecorder referenceRateAudit;
    @Autowired FaultInjectingPricingAuditRecorder pricingAudit;

    @AfterEach
    void disarmFaults() {
        exchangeRateAudit.disarm();
        referenceRateAudit.disarm();
        pricingAudit.disarm();
    }

    @Test
    void exchangeRateAndAuditEventRollBackWhenAuditRecorderFails() {
        String actor = uniqueActor("exchange");
        int rateRowsBefore = rowCount("select count(*) from exchange_rates where created_by=?", actor);
        int auditRowsBefore = auditRows(actor, "EXCHANGE_RATE_RECORDED");
        exchangeRateAudit.failNext();

        assertThatThrownBy(() -> currency.recordObservation(
                        "BRL",
                        "USD",
                        new BigDecimal("0.2000000000"),
                        "atomicity-test",
                        Instant.parse("2030-01-15T11:59:00Z"),
                        actor))
                .isInstanceOf(InjectedAuditFailure.class);

        assertSoftly(softly -> {
            softly.assertThat(rowCount(
                            "select count(*) from exchange_rates where created_by=?", actor))
                    .isEqualTo(rateRowsBefore);
            softly.assertThat(auditRows(actor, "EXCHANGE_RATE_RECORDED"))
                    .isEqualTo(auditRowsBefore);
            softly.assertThat(AopUtils.isAopProxy(currency)).isTrue();
        });
    }

    @Test
    void baseRateAndAuditEventRollBackWhenAuditRecorderFails() {
        String actor = uniqueActor("base");
        int rateRowsBefore =
                rowCount("select count(*) from base_rate_versions where created_by=?", actor);
        int auditRowsBefore = auditRows(actor, "BASE_RATE_RECORDED");
        referenceRateAudit.failNext("BASE_RATE_RECORDED");

        assertThatThrownBy(() -> referenceRates.recordBaseRate(
                        "BRL",
                        new BigDecimal("0.0100000000"),
                        Instant.parse("2030-01-01T00:00:00Z"),
                        actor))
                .isInstanceOf(InjectedAuditFailure.class);

        assertSoftly(softly -> {
            softly.assertThat(rowCount(
                            "select count(*) from base_rate_versions where created_by=?", actor))
                    .isEqualTo(rateRowsBefore);
            softly.assertThat(auditRows(actor, "BASE_RATE_RECORDED"))
                    .isEqualTo(auditRowsBefore);
            softly.assertThat(AopUtils.isAopProxy(referenceRates)).isTrue();
        });
    }

    @Test
    void productSpreadAndAuditEventRollBackWhenAuditRecorderFails() {
        String actor = uniqueActor("spread");
        int spreadRowsBefore =
                rowCount("select count(*) from product_spread_versions where created_by=?", actor);
        int auditRowsBefore = auditRows(actor, "PRODUCT_SPREAD_RECORDED");
        referenceRateAudit.failNext("PRODUCT_SPREAD_RECORDED");

        assertThatThrownBy(() -> referenceRates.recordProductSpread(
                        "MERCANTILE_INVOICE",
                        new BigDecimal("0.0050000000"),
                        Instant.parse("2030-01-01T00:00:00Z"),
                        actor))
                .isInstanceOf(InjectedAuditFailure.class);

        assertSoftly(softly -> {
            softly.assertThat(rowCount(
                            "select count(*) from product_spread_versions where created_by=?",
                            actor))
                    .isEqualTo(spreadRowsBefore);
            softly.assertThat(auditRows(actor, "PRODUCT_SPREAD_RECORDED"))
                    .isEqualTo(auditRowsBefore);
            softly.assertThat(AopUtils.isAopProxy(referenceRates)).isTrue();
        });
    }

    @Test
    void pricingQuoteAndAuditEventRollBackWhenAuditRecorderFails() {
        UUID assignorId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        String fixtureActor = uniqueActor("quote-fixture");
        String quoteActor = uniqueActor("quote");
        referenceRates.recordBaseRate(
                "BRL",
                new BigDecimal("0.0100000000"),
                Instant.parse("2029-12-15T06:07:08Z"),
                fixtureActor);
        referenceRates.recordProductSpread(
                "MERCANTILE_INVOICE",
                new BigDecimal("0.0050000000"),
                Instant.parse("2029-12-15T06:07:08Z"),
                fixtureActor);
        assignors.create(new AssignorService.CreateCommand(
                assignorId,
                "Audit Atomicity Co",
                "ATM" + assignorId.toString().substring(0, 8),
                true,
                fixtureActor));
        receivables.register(new ReceivableService.RegisterCommand(
                receivableId,
                assignorId,
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.00"),
                "BRL",
                LocalDate.parse("2030-01-01"),
                LocalDate.parse("2030-02-14"),
                fixtureActor));
        int quoteRowsBefore =
                rowCount("select count(*) from pricing_quotes where created_by=?", quoteActor);
        int auditRowsBefore = auditRows(quoteActor, "QUOTE_CREATED");
        pricingAudit.failNext();

        assertThatThrownBy(() -> pricing.createQuote(receivableId, "BRL", quoteActor))
                .isInstanceOf(InjectedAuditFailure.class);

        assertSoftly(softly -> {
            softly.assertThat(rowCount(
                            "select count(*) from pricing_quotes where created_by=?", quoteActor))
                    .isEqualTo(quoteRowsBefore);
            softly.assertThat(auditRows(quoteActor, "QUOTE_CREATED"))
                    .isEqualTo(auditRowsBefore);
            softly.assertThat(AopUtils.isAopProxy(pricing)).isTrue();
        });
    }

    private int auditRows(String actor, String action) {
        return rowCount(
                "select count(*) from audit_events where actor=? and action=?", actor, action);
    }

    private int rowCount(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private static String uniqueActor(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@srm.local";
    }
}
