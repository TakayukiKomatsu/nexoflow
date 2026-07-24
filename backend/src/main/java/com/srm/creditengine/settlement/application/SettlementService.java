package com.srm.creditengine.settlement.application;

import com.srm.creditengine.settlement.domain.SettlementPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Authoritative seam for non-reserving previews and all-or-nothing settlement. */
public interface SettlementService {
    int MAX_QUOTE_IDS = SettlementPolicy.MAX_QUOTE_IDS;

    Preview preview(List<UUID> orderedQuoteIds, String actor);
    Result settle(List<UUID> orderedQuoteIds, String idempotencyKey, String actor);
    Result get(UUID settlementId);
    Reversal reverse(UUID settlementId, String reason, String idempotencyKey, String actor);

    record Item(UUID quoteId, UUID receivableId, BigDecimal settlementAmount) {}
    record Preview(List<Item> items, String settlementCurrency, BigDecimal totalAmount, Instant asOf, Instant earliestExpiry) {}
    record Result(UUID settlementId, String status, List<Item> items, String settlementCurrency, BigDecimal totalAmount, Instant completedAt, boolean replayed) {}
    record Reversal(UUID reversalId, UUID settlementId, String reason, Instant reversedAt, boolean replayed) {}
}
