package com.srm.creditengine.settlement.domain;

import java.util.List;
import java.util.UUID;

/** Settlement header and ordered Receivables held under a reversal lock. */
public record LockedSettlement(UUID settlementId, List<LockedReceivable> receivables) {
}
