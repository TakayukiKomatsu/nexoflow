package com.srm.creditengine.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementPolicyTest {
    private static final Instant NOW = Instant.parse("2030-01-15T12:00:00Z");

    @Test
    void rejectsDuplicateQuoteIdsBeforePersistence() {
        var quoteId = UUID.randomUUID();

        assertThatThrownBy(() -> SettlementPolicy.requireOrderedUnique(List.of(quoteId, quoteId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ordered and unique");
    }

    @Test
    void previewRequiresOneAssignorAndSettlementCurrency() {
        var assignor = UUID.randomUUID();
        var brlQuote = quote(assignor, "BRL", "ACTIVE", "REGISTERED", NOW.plusSeconds(60));
        var usdQuote = quote(assignor, "USD", "ACTIVE", "REGISTERED", NOW.plusSeconds(60));

        assertThatThrownBy(() -> SettlementPolicy.validateQuotes(
                List.of(brlQuote.quoteId(), usdQuote.quoteId()), List.of(brlQuote, usdQuote), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one assignor and settlement currency");
    }

    @Test
    void previewRejectsTwoQuotesForTheSameReceivableBeforeAggregation() {
        var assignor = UUID.randomUUID();
        var receivableId = UUID.randomUUID();
        var first = quote(assignor, receivableId, "BRL", "ACTIVE", "REGISTERED", NOW.plusSeconds(60));
        var second = quote(assignor, receivableId, "BRL", "ACTIVE", "REGISTERED", NOW.plusSeconds(60));

        assertThatThrownBy(() -> SettlementPolicy.validateQuotes(
                        List.of(first.quoteId(), second.quoteId()), List.of(first, second), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pricing quotes must reference unique receivables");
    }

    @Test
    void validatesOrderedBatchAndBuildsPreview() {
        var assignor = UUID.randomUUID();
        var first = quote(assignor, "BRL", "ACTIVE", "REGISTERED", NOW.plusSeconds(120));
        var second = quote(assignor, "BRL", "ACTIVE", "REGISTERED", NOW.plusSeconds(60));

        var validated = SettlementPolicy.validateQuotes(
                List.of(second.quoteId(), first.quoteId()), List.of(first, second), NOW);
        var preview = SettlementPolicy.previewOf(validated, NOW);

        assertThat(preview.items()).extracting(item -> item.quoteId()).containsExactly(second.quoteId(), first.quoteId());
        assertThat(preview.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(preview.earliestExpiry()).isEqualTo(second.expiresAt());
    }

    @Test
    void rejectsMissingExpiredConsumedAndAlreadySettledQuotes() {
        var assignor = UUID.randomUUID();
        var active = quote(assignor, "BRL", "ACTIVE", "REGISTERED", NOW.plusSeconds(60));

        assertThatThrownBy(() -> SettlementPolicy.validateQuotes(List.of(active.quoteId()), List.of(), NOW))
                .hasMessage("One or more pricing quotes were not found");
        assertThatThrownBy(() -> SettlementPolicy.validateQuotes(List.of(active.quoteId()),
                List.of(quote(assignor, "BRL", "ACTIVE", "REGISTERED", NOW.plusSeconds(60))), NOW))
                .hasMessage("One or more pricing quotes were not found");
        assertThatThrownBy(() -> SettlementPolicy.validateQuotes(List.of(active.quoteId()),
                List.of(new LockedQuote(active.quoteId(), active.receivableId(), "BRL", BigDecimal.TEN,
                        NOW, "ACTIVE", assignor, "REGISTERED", 0, "BRL", "INVOICE")), NOW))
                .isInstanceOf(PricingQuoteExpiredException.class);
        assertThatThrownBy(() -> SettlementPolicy.validateQuotes(List.of(active.quoteId()),
                List.of(new LockedQuote(active.quoteId(), active.receivableId(), "BRL", BigDecimal.TEN,
                        NOW.plusSeconds(60), "CONSUMED", assignor, "REGISTERED", 0, "BRL", "INVOICE")), NOW))
                .isInstanceOf(AlreadySettledException.class);
        assertThatThrownBy(() -> SettlementPolicy.validateQuotes(List.of(active.quoteId()),
                List.of(new LockedQuote(active.quoteId(), active.receivableId(), "BRL", BigDecimal.TEN,
                        NOW.plusSeconds(60), "ACTIVE", assignor, "SETTLED", 0, "BRL", "INVOICE")), NOW))
                .isInstanceOf(AlreadySettledException.class);
    }

    @Test
    void hashesCanonicalCommandsAndValidatesReversalInput() {
        var settlementId = UUID.randomUUID();
        var quoteId = UUID.randomUUID();

        assertThat(SettlementPolicy.requestHash(List.of(quoteId))).isEqualTo(SettlementPolicy.requestHash(List.of(quoteId)));
        assertThat(SettlementPolicy.reversalRequestHash(settlementId, " reason "))
                .isEqualTo(SettlementPolicy.reversalRequestHash(settlementId, "reason"));
        assertThatThrownBy(() -> SettlementPolicy.validateReversalInput(null, "reason"))
                .hasMessage("A reversal reason is required");
        assertThatThrownBy(() -> SettlementPolicy.validateReversalInput(settlementId, " "))
                .hasMessage("A reversal reason is required");
    }

    private static LockedQuote quote(UUID assignor, String currency, String quoteStatus, String receivableStatus, Instant expiresAt) {
        return quote(assignor, UUID.randomUUID(), currency, quoteStatus, receivableStatus, expiresAt);
    }

    private static LockedQuote quote(
            UUID assignor,
            UUID receivableId,
            String currency,
            String quoteStatus,
            String receivableStatus,
            Instant expiresAt) {
        return new LockedQuote(UUID.randomUUID(), receivableId, currency, new BigDecimal("10.00"), expiresAt,
                quoteStatus, assignor, receivableStatus, 0, "BRL", "MERCANTILE_INVOICE");
    }
}
