package com.srm.creditengine.settlement.application;

import java.time.Instant;
import java.util.UUID;

/** Scoped command deduplication boundary. Callers hold the returned row within their transaction. */
public interface IdempotencyRepository {
    IdempotencyRecord claim(String actor, String operation, String key, String requestHash, Instant createdAt);

    void completeSettlement(UUID recordId, UUID settlementId, Instant completedAt);

    void completeReversal(UUID recordId, UUID reversalId, Instant completedAt);

    record IdempotencyRecord(UUID id, String requestHash, UUID settlementId, UUID reversalId, String status) {
        public boolean completed() {
            return "COMPLETED".equals(status);
        }
    }
}
