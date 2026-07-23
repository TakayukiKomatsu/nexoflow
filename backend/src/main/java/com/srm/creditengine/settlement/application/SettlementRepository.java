package com.srm.creditengine.settlement.application;

import com.srm.creditengine.settlement.domain.LockedQuote;
import com.srm.creditengine.settlement.domain.LockedSettlement;
import com.srm.creditengine.settlement.domain.SettlementDraft;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Transactional persistence boundary for settlement locks, writes, and result rehydration. */
public interface SettlementRepository {
    List<LockedQuote> findQuotes(List<UUID> orderedIds);

    List<LockedQuote> lockQuotes(List<UUID> orderedIds);

    void saveCompleted(SettlementDraft draft);

    void consumeQuoteAndReceivable(LockedQuote quote);

    void saveItem(SettlementDraft draft, LockedQuote quote, int position);

    Optional<SettlementService.Result> findResult(UUID settlementId);

    LockedSettlement lockSettlement(UUID settlementId);

    SettlementService.Reversal reverse(LockedSettlement settlement, String reason, Instant at, String actor);

    Optional<SettlementService.Reversal> findReversal(UUID reversalId);
}
