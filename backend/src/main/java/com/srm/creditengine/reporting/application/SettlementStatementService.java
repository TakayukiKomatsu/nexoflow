package com.srm.creditengine.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only ledger seam. Its SQL implementation owns joins, filters, ordering and bounded pagination. */
public interface SettlementStatementService {
    Page query(Filter filter);
    record Filter(Instant from, Instant to, UUID assignorId, String assetCurrency, String settlementCurrency, String productType, int page, int size) {}
    record Entry(UUID entryId, String entryType, BigDecimal signedAmount, Instant effectiveAt, UUID settlementId, UUID reversalId, UUID assignorId, String assetCurrency, String settlementCurrency, String productType, UUID receivableId) {}
    record Page(List<Entry> entries, int page, int size, boolean hasNext) {}
}
