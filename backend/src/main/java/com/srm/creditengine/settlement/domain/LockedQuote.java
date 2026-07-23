package com.srm.creditengine.settlement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Quote and receivable state read under the settlement lock order. */
public record LockedQuote(
        UUID quoteId,
        UUID receivableId,
        String settlementCurrency,
        BigDecimal settlementAmount,
        Instant expiresAt,
        String quoteStatus,
        UUID assignorId,
        String receivableStatus,
        long receivableVersion,
        String assetCurrency,
        String productType) {
}
