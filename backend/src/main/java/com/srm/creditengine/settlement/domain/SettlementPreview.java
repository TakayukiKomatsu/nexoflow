package com.srm.creditengine.settlement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SettlementPreview(
        List<Item> items,
        String settlementCurrency,
        BigDecimal totalAmount,
        Instant asOf,
        Instant earliestExpiry) {

    public record Item(UUID quoteId, UUID receivableId, BigDecimal settlementAmount) {
    }
}
