package com.srm.creditengine.settlement.domain;

import java.util.List;
import java.util.UUID;

/** Settlement header and ordered receivables held under a reversal lock. */
public record LockedSettlement(UUID settlementId, List<UUID> receivableIds) {
}
