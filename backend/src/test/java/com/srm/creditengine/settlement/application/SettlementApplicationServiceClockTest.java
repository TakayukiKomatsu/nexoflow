package com.srm.creditengine.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.srm.creditengine.settlement.domain.LockedQuote;
import com.srm.creditengine.settlement.domain.PricingQuoteExpiredException;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementApplicationServiceClockTest {
    private static final Instant BEFORE_EXPIRY = Instant.parse("2030-01-15T12:00:00Z");
    private static final Instant EXPIRY = BEFORE_EXPIRY.plusSeconds(5);
    private static final UUID QUOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Test
    void settlementValidatesExpiryAfterTheBlockingQuoteLockReturns() {
        MutableClock clock = new MutableClock(BEFORE_EXPIRY);
        SettlementRepository settlements = mock(SettlementRepository.class);
        IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
        when(idempotency.claim(anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> new IdempotencyRepository.IdempotencyRecord(
                        UUID.randomUUID(), invocation.getArgument(3), null, null, "PROCESSING"));
        when(settlements.lockQuotes(List.of(QUOTE_ID))).thenAnswer(invocation -> {
            clock.set(EXPIRY);
            return List.of(activeQuote());
        });
        SettlementApplicationService service = service(settlements, idempotency, clock);

        assertThatThrownBy(() -> service.settle(List.of(QUOTE_ID), "key", "operator"))
                .isInstanceOf(PricingQuoteExpiredException.class);
        verify(settlements, never()).saveCompleted(any());
    }

    @Test
    void previewReportsTheValidationInstantAfterItsReadReturns() {
        MutableClock clock = new MutableClock(BEFORE_EXPIRY);
        SettlementRepository settlements = mock(SettlementRepository.class);
        when(settlements.findQuotes(List.of(QUOTE_ID))).thenAnswer(invocation -> {
            clock.set(BEFORE_EXPIRY.plusSeconds(2));
            return List.of(activeQuote());
        });
        SettlementApplicationService service = service(settlements, mock(IdempotencyRepository.class), clock);

        assertThat(service.preview(List.of(QUOTE_ID), "operator").asOf())
                .isEqualTo(BEFORE_EXPIRY.plusSeconds(2));
    }

    private static SettlementApplicationService service(
            SettlementRepository settlements, IdempotencyRepository idempotency, Clock clock) {
        return new SettlementApplicationService(
                settlements,
                idempotency,
                mock(AuditEventRecorder.class),
                clock,
                mock(FinancialTelemetry.class));
    }

    private static LockedQuote activeQuote() {
        return new LockedQuote(
                QUOTE_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                "BRL",
                new BigDecimal("100.00"),
                EXPIRY,
                "ACTIVE",
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "REGISTERED",
                0L,
                "BRL",
                "MERCANTILE_INVOICE");
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        private void set(Instant instant) {
            current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("Only UTC is supported");
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
