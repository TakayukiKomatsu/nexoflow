package com.srm.creditengine.settlement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable validated batch ready for the single atomic persistence transaction. */
public record SettlementDraft(
        UUID settlementId,
        UUID assignorId,
        String settlementCurrency,
        BigDecimal totalAmount,
        List<LockedQuote> quotes,
        Instant createdAt,
        String actor) {
}
